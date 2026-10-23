package dev.linakbed.domain.safety

import dev.linakbed.domain.controller.MotionKind
import dev.linakbed.domain.controller.MotionOutcome
import dev.linakbed.domain.port.BedLink
import dev.linakbed.domain.port.LinkState
import dev.linakbed.protocol.Actuator
import dev.linakbed.protocol.HeightReading
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.TimeSource

/** 동작 시작 시점의 센서 상태. [lastRaw]는 마지막으로 받은 원시값, [sensorsAvailable]은 센서 값을 받은 적이 있는지. */
internal data class SensorBaseline(
    val lastRaw: Map<Actuator, Int>,
    val sensorsAvailable: Boolean,
)

/**
 * 동작 하나를 감시해 끝낼 이유를 돌려준다. S1(최대 시간)은 [dev.linakbed.domain.controller.MotionRunner]가 시간 제한으로 건다.
 * - S5: 연결이 끊기면 `SafetyStopped(DISCONNECTED)`
 * - S7: 수동 이동 중 센서가 [SafetyConfig.stallTimeout] 동안 변하지 않으면 `SafetyStopped(STALL)` (센서가 있을 때만)
 * - 프리셋: 움직인 뒤 [SafetyConfig.presetSettle] 안정 → 도착, 움직임 없이 stallTimeout → 완료, 센서 없으면 고정 대기 후 완료
 */
internal class SafetySupervisor(
    private val config: SafetyConfig,
    private val timeSource: TimeSource.WithComparableMarks,
) {
    suspend fun watch(
        link: BedLink,
        kind: MotionKind,
        baseline: SensorBaseline,
    ): MotionOutcome =
        coroutineScope {
            val activity = SensorActivity(baseline, timeSource)
            val end = CompletableDeferred<MotionOutcome>()
            val watchers =
                listOf(
                    launch { link.telemetry.collect(activity::record) },
                    launch {
                        link.state.first { it != LinkState.Connected }
                        end.complete(MotionOutcome.SafetyStopped(SafetyReason.DISCONNECTED))
                    },
                    launch {
                        while (!end.isCompleted) {
                            delay(config.monitorInterval)
                            evaluate(kind, activity)?.let(end::complete)
                        }
                    },
                )
            end.await().also { watchers.forEach { it.cancel() } }
        }

    private fun evaluate(
        kind: MotionKind,
        activity: SensorActivity,
    ): MotionOutcome? =
        when (kind) {
            MotionKind.MANUAL_MOVE ->
                if (activity.available && activity.sinceChange() >= config.stallTimeout) {
                    MotionOutcome.SafetyStopped(SafetyReason.STALL)
                } else {
                    null
                }
            MotionKind.PRESET -> presetOutcome(activity)
        }

    private fun presetOutcome(activity: SensorActivity): MotionOutcome? {
        val done =
            when {
                !activity.available -> activity.sinceStart() >= config.presetWithoutSensorDuration
                activity.moved -> activity.sinceChange() >= config.presetSettle
                else -> activity.sinceChange() >= config.stallTimeout
            }
        return if (done) MotionOutcome.Completed else null
    }
}

/** 센서 변화 추적. 수집과 판단이 다른 스레드에서 돌 수 있어 동기화한다. */
private class SensorActivity(
    baseline: SensorBaseline,
    private val timeSource: TimeSource.WithComparableMarks,
) {
    private val last = baseline.lastRaw.toMutableMap()
    private val start = timeSource.markNow()
    private var lastChange = start
    private var everMoved = false
    private var sawSensors = baseline.sensorsAvailable

    val available: Boolean @Synchronized get() = sawSensors
    val moved: Boolean @Synchronized get() = everMoved

    @Synchronized
    fun record(reading: HeightReading) {
        sawSensors = true
        val previous = last.put(reading.actuator, reading.raw)
        if (previous != reading.raw) {
            if (previous != null) everMoved = true
            lastChange = timeSource.markNow()
        }
    }

    @Synchronized
    fun sinceChange(): Duration = lastChange.elapsedNow()

    fun sinceStart(): Duration = start.elapsedNow()
}
