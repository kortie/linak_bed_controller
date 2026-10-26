package dev.linakbed.domain.controller

import dev.linakbed.domain.BedError
import dev.linakbed.domain.BedResult
import dev.linakbed.domain.andThen
import dev.linakbed.domain.log.ExecutionEntry
import dev.linakbed.domain.log.info
import dev.linakbed.domain.log.warn
import dev.linakbed.domain.port.BedLink
import dev.linakbed.domain.port.ExecutionLog
import dev.linakbed.domain.port.LinkState
import dev.linakbed.domain.port.PairedBedSource
import dev.linakbed.domain.safety.SafetySupervisor
import dev.linakbed.domain.safety.SensorBaseline
import dev.linakbed.protocol.Actuator
import dev.linakbed.protocol.BedCommand
import dev.linakbed.protocol.CommandTiming
import dev.linakbed.protocol.Direction
import dev.linakbed.protocol.HeightReading
import dev.linakbed.protocol.LightAction
import dev.linakbed.protocol.PresetSlot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration

/**
 * [BedController] 구현. 모터 동작은 [scope]의 코루틴 하나로 실행되고, 끝나는 이유와 상관없이 마지막에 Stop을 보낸다
 * (STOP 요청으로 끝난 경우만 [stop]이 보낸다).
 *
 * 실행 기록 책임(아키텍처 §5.3): MANUAL 동작 결과와 모든 안전 정지만 여기서 기록한다. REMOTE 결과는 원격 핸들러(S06)가 기록한다.
 */
class DefaultBedController(
    private val scope: CoroutineScope,
    private val link: BedLink,
    private val bed: PairedBedSource,
    private val executionLog: ExecutionLog,
    config: BedControllerConfig,
    environment: ControllerEnvironment,
) : BedController {
    private val profile = config.profile
    private val safety = config.safety
    private val clock = environment.clock
    private val logger = environment.logger
    private val runner = MotionRunner(link, SafetySupervisor(safety, environment.timeSource))

    private val mutableStatus = MutableStateFlow(BedStatus())
    override val status: StateFlow<BedStatus> = mutableStatus.asStateFlow()

    private val startMutex = Mutex()
    private val current = AtomicReference<Motion?>(null)
    private val lastRaw = ConcurrentHashMap<Actuator, Int>()

    @Volatile
    private var sensorsSeen = false
    private val leases = AtomicInteger(0)
    private val idleLock = Any()
    private var idleJob: Job? = null

    private class Motion(
        val active: ActiveMotion,
    ) {
        val interrupt = CompletableDeferred<InterruptReason>()
        val done = CompletableDeferred<MotionOutcome>()
    }

    private data class MotionRequest(
        val active: ActiveMotion,
        val command: BedCommand,
        val timing: CommandTiming,
        val maxDuration: Duration,
        /** 실행 기록의 action 문자열. */
        val label: String,
    )

    init {
        scope.launch { link.state.collect(::onLinkState) }
        scope.launch { link.telemetry.collect(::onReading) }
    }

    override suspend fun startManualMove(
        actuator: Actuator,
        direction: Direction,
    ): BedResult<MotionHandle> =
        startMotion(
            MotionRequest(
                active = ActiveMotion(CommandSource.MANUAL, MotionKind.MANUAL_MOVE),
                command = BedCommand.Move(actuator, direction),
                timing = profile.moveTiming,
                maxDuration = safety.manualMaxDuration,
                label = "manual_move $actuator $direction",
            ),
        ).andThen { motion -> BedResult.Ok(MotionHandle { motion.interrupt.complete(InterruptReason.Released) }) }

    override suspend fun goToPreset(
        slot: PresetSlot,
        source: CommandSource,
    ): BedResult<Unit> =
        startMotion(
            MotionRequest(
                active = ActiveMotion(source, MotionKind.PRESET, slot),
                command = BedCommand.RecallPreset(slot),
                timing = profile.presetTiming,
                maxDuration = safety.presetMaxDuration,
                label = "preset $slot",
            ),
        ).andThen { motion -> motion.done.await().toResult() }

    override suspend fun savePreset(slot: PresetSlot): BedResult<Unit> =
        startMutex.withLock {
            // 저장은 MANUAL이므로 중재 표상 항상 진행 중 동작을 교체한다.
            interruptCurrent(InterruptReason.Replaced(CommandSource.MANUAL))
            cancelIdle()
            val result = ensureConnected().andThen { link.write(BedCommand.SavePreset(slot)) }
            record(CommandSource.MANUAL, "save_preset $slot", result.describe())
            scheduleIdle()
            result
        }

    override suspend fun setLight(
        action: LightAction,
        source: CommandSource,
    ): BedResult<Unit> {
        cancelIdle()
        val result = ensureConnected().andThen { link.write(BedCommand.Light(action)) }
        if (result is BedResult.Ok) {
            val light =
                when (action) {
                    LightAction.ON -> LightState.ON
                    LightAction.OFF -> LightState.OFF
                    LightAction.TOGGLE -> LightState.UNKNOWN
                }
            mutableStatus.update { it.copy(light = light) }
        }
        if (source == CommandSource.MANUAL) record(source, "light $action", result.describe())
        scheduleIdle()
        return result
    }

    override suspend fun stop(source: CommandSource): BedResult<Unit> {
        // 먼저 진행 중 동작을 끝내 대기 중인 이동 쓰기를 취소한 뒤 Stop을 보낸다 (아키텍처 §6).
        current.get()?.let { motion ->
            motion.interrupt.complete(InterruptReason.Stopped(source))
            motion.done.await()
        }
        val result = sendStop()
        if (source == CommandSource.MANUAL) record(source, "stop", result.describe())
        scheduleIdle()
        return result
    }

    override fun holdConnection(): ConnectionLease {
        leases.incrementAndGet()
        cancelIdle()
        scope.launch {
            val result = ensureConnected()
            if (result is BedResult.Err) mutableStatus.update { it.copy(lastError = result.error) }
        }
        val released = AtomicBoolean(false)
        return ConnectionLease {
            if (released.compareAndSet(false, true) && leases.decrementAndGet() == 0) scheduleIdle()
        }
    }

    private suspend fun startMotion(request: MotionRequest): BedResult<Motion> =
        startMutex.withLock {
            val source = request.active.source
            when (val decision = CommandArbiter.decide(current.get()?.active?.source, source)) {
                is CommandArbiter.Decision.Reject -> {
                    logger.info(AREA, "rejected ${request.label} from $source: ${decision.activeSource} active")
                    return@withLock BedResult.Err(BedError.Busy(decision.activeSource))
                }
                CommandArbiter.Decision.Replace -> interruptCurrent(InterruptReason.Replaced(source))
                CommandArbiter.Decision.Start -> Unit
            }
            cancelIdle()
            when (val connected = ensureConnected()) {
                is BedResult.Err -> {
                    mutableStatus.update { it.copy(lastError = connected.error) }
                    if (source == CommandSource.MANUAL) record(source, request.label, connected.describe())
                    scheduleIdle()
                    connected
                }
                is BedResult.Ok -> BedResult.Ok(launchMotion(request))
            }
        }

    private fun launchMotion(request: MotionRequest): Motion {
        val motion = Motion(request.active)
        current.set(motion)
        mutableStatus.update { it.copy(activeMotion = request.active, lastError = null) }
        logger.info(AREA, "start ${request.label} (${request.active.source})")
        val baseline = SensorBaseline(lastRaw.toMap(), sensorsSeen)
        scope.launch {
            val outcome =
                coroutineScope {
                    val running = async { runner.run(request.command, request.active.kind, request.timing, request.maxDuration, baseline) }
                    val interrupted = async { MotionOutcome.Interrupted(motion.interrupt.await()) }
                    select {
                        running.onAwait { it }
                        interrupted.onAwait { it }
                    }.also {
                        running.cancel()
                        interrupted.cancel()
                    }
                }
            withContext(NonCancellable) {
                if (outcome.needsStop) sendStop()
                finish(motion, request, outcome)
            }
        }
        return motion
    }

    private suspend fun finish(
        motion: Motion,
        request: MotionRequest,
        outcome: MotionOutcome,
    ) {
        current.compareAndSet(motion, null)
        val error = outcome.errorOrNull
        mutableStatus.update { it.copy(activeMotion = null, lastError = error ?: it.lastError) }
        if (outcome is MotionOutcome.SafetyStopped) {
            logger.warn(SAFETY_AREA, "${request.label} (${request.active.source}) stopped: ${outcome.reason}")
        } else {
            logger.info(AREA, "${request.label} (${request.active.source}) ended: ${outcome.describe()}")
        }
        if (request.active.source == CommandSource.MANUAL || outcome is MotionOutcome.SafetyStopped) {
            record(request.active.source, request.label, outcome.describe())
        }
        motion.done.complete(outcome)
        scheduleIdle()
    }

    /** 진행 중 동작을 [reason]으로 끝내고, Stop 전송·정리가 끝날 때까지 기다린다. */
    private suspend fun interruptCurrent(reason: InterruptReason) {
        val motion = current.get() ?: return
        motion.interrupt.complete(reason)
        motion.done.await()
    }

    /** S2: Stop은 실패하면 [dev.linakbed.domain.safety.SafetyConfig.stopRetries]번 더 시도한다. 연결이 없으면 보낼 곳이 없으므로 재시도하지 않는다. */
    private suspend fun sendStop(): BedResult<Unit> {
        var result = link.write(BedCommand.Stop)
        var retries = 0
        while (result is BedResult.Err && result.error != BedError.Disconnected && retries < safety.stopRetries) {
            delay(safety.stopRetryInterval)
            retries++
            result = link.write(BedCommand.Stop)
        }
        if (result is BedResult.Err) logger.warn(SAFETY_AREA, "stop failed after $retries retries: ${result.error}")
        return result
    }

    /** 동작 전 연결 보장. 연결 실패는 동작 시작 전이므로 1회 재시도한다 (S5: 동작 중 끊김은 재시도하지 않음). */
    private suspend fun ensureConnected(): BedResult<Unit> {
        if (link.state.value == LinkState.Connected) return BedResult.Ok(Unit)
        val device = bed.current()
        return if (device == null) BedResult.Err(BedError.NotPaired) else connectWithRetry(device.address)
    }

    private suspend fun connectWithRetry(address: String): BedResult<Unit> {
        val first = link.connect(address, safety.connectTimeout)
        return if (first is BedResult.Err && first.error.isRetryable()) {
            logger.info(AREA, "connect failed (${first.error}), retrying once")
            link.connect(address, safety.connectTimeout)
        } else {
            first
        }
    }

    private fun BedError.isRetryable(): Boolean =
        this == BedError.ConnectTimeout || this == BedError.Disconnected || this is BedError.GattFailure

    private fun onLinkState(state: LinkState) {
        mutableStatus.update { it.copy(link = state, light = if (state == LinkState.Connected) it.light else LightState.UNKNOWN) }
    }

    private fun onReading(reading: HeightReading) {
        sensorsSeen = true
        lastRaw[reading.actuator] = reading.raw
        mutableStatus.update {
            when (reading.actuator) {
                Actuator.HEAD -> it.copy(headCm = reading.cm)
                Actuator.FEET -> it.copy(feetCm = reading.cm)
                Actuator.BOTH -> it
            }
        }
    }

    /** ADR-003: 동작도 연결 유지 요청도 없으면 [dev.linakbed.domain.safety.SafetyConfig.idleDisconnect] 후 연결 해제. */
    private fun scheduleIdle() {
        synchronized(idleLock) {
            idleJob?.cancel()
            idleJob =
                if (leases.get() > 0 || current.get() != null) {
                    null
                } else {
                    scope.launch {
                        delay(safety.idleDisconnect)
                        if (leases.get() == 0 && current.get() == null && link.state.value == LinkState.Connected) {
                            logger.info(AREA, "idle for ${safety.idleDisconnect}, disconnecting")
                            link.disconnect()
                        }
                    }
                }
        }
    }

    private fun cancelIdle() {
        synchronized(idleLock) {
            idleJob?.cancel()
            idleJob = null
        }
    }

    private suspend fun record(
        source: CommandSource,
        action: String,
        result: String,
    ) {
        executionLog.record(ExecutionEntry(at = clock.now(), source = source, action = action, result = result))
    }

    private fun BedResult<Unit>.describe(): String =
        when (this) {
            is BedResult.Ok -> "ok"
            is BedResult.Err -> "error:$error"
        }

    private companion object {
        const val AREA = "Controller"
        const val SAFETY_AREA = "Safety"
    }
}
