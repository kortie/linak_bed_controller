package dev.linakbed.domain.remote

import dev.linakbed.domain.BedResult
import dev.linakbed.domain.controller.BedActions
import dev.linakbed.domain.controller.CommandSource
import dev.linakbed.domain.log.ExecutionEntry
import dev.linakbed.domain.port.Clock
import dev.linakbed.domain.port.ExecutionLog
import dev.linakbed.domain.port.MotionConfirmation
import dev.linakbed.domain.port.RemoteSettingsStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration

/**
 * [RemoteCommandHandler] 구현. 채널을 모른다 — [RemoteRequest]만 다룬다 (ADR-006).
 * [controller]는 모든 침대 묶음이다 — 원격 명령은 연결된 모든 침대에 같은 번호로 (ADR-012).
 * 원격 요청의 결과·거절 기록은 여기서만 한다 (아키텍처 §5.3 실행 기록 책임).
 */
class DefaultRemoteCommandHandler(
    private val controller: BedActions,
    private val executionLog: ExecutionLog,
    private val settingsStore: RemoteSettingsStore,
    private val confirmation: MotionConfirmation,
    private val clock: Clock,
) : RemoteCommandHandler {
    @Volatile
    private var currentSettings = RemoteSettings()
    private val policy = RemoteCommandPolicy({ currentSettings }, clock)

    /** S9: 실행 전 대기 중인 요청은 하나만. */
    private val waitingLock = Any()
    private var waiting: Waiting? = null

    private class Waiting {
        val superseded = CompletableDeferred<Unit>()
    }

    override suspend fun handle(request: RemoteRequest): RemoteOutcome {
        currentSettings = settingsStore.settings().first()
        val result =
            when (val decision = policy.evaluate(request)) {
                is PolicyDecision.Reject -> RemoteResult.Rejected(decision.reason)
                PolicyDecision.Accept -> admit(request, currentSettings)
            }
        record(request, result)
        return RemoteOutcome(request.requestId, result)
    }

    override suspend fun reject(
        channel: RemoteChannel,
        requestId: String,
        reason: RejectReason,
        detail: String?,
    ) {
        executionLog.record(
            ExecutionEntry(
                at = clock.now(),
                source = CommandSource.REMOTE,
                channel = channel,
                requestId = requestId,
                action = "-",
                result = RemoteResult.Rejected(reason).describe(),
                detail = detail,
            ),
        )
    }

    private suspend fun admit(
        request: RemoteRequest,
        settings: RemoteSettings,
    ): RemoteResult =
        when (val command = request.command) {
            // 정지는 대기 중인 프리셋을 끝내고 바로 실행한다 (S2: 정지를 늦추지 않음).
            RemoteCommand.Stop -> {
                supersedeWaiting()
                execute(command)
            }
            is RemoteCommand.Light -> execute(command)
            is RemoteCommand.Preset -> {
                val delay = settings.preExecutionDelay.coerceAtMost(RemoteSettings.MAX_PRE_EXECUTION_DELAY)
                if (delay.isPositive()) {
                    waitForCancel(request, delay)?.let { RemoteResult.Rejected(it) } ?: execute(command)
                } else {
                    execute(command)
                }
            }
        }

    /** S9: [delay] 동안 취소 화면을 띄우고 기다린다. 취소·대체되면 그 사유, 시간이 다 되면 null. */
    private suspend fun waitForCancel(
        request: RemoteRequest,
        delay: Duration,
    ): RejectReason? {
        val mine = Waiting()
        supersedeWaiting()
        synchronized(waitingLock) { waiting = mine }
        val pending = confirmation.show(request, delay)
        return try {
            withTimeoutOrNull(delay) {
                coroutineScope {
                    val userCancel = async { pending.awaitCancel() }
                    val manualMove = async { manualMotionStarts() }
                    val superseded = async { mine.superseded.await() }
                    select {
                        userCancel.onAwait { RejectReason.CANCELLED_BY_USER }
                        manualMove.onAwait { RejectReason.CANCELLED_BY_USER }
                        superseded.onAwait { RejectReason.SUPERSEDED }
                    }.also {
                        userCancel.cancel()
                        manualMove.cancel()
                        superseded.cancel()
                    }
                }
            }
        } finally {
            pending.dismiss()
            synchronized(waitingLock) { if (waiting === mine) waiting = null }
        }
    }

    /** 대기 시작 뒤 사용자가 앱에서 수동 모터 조작을 시작하면 반환 (이미 진행 중이던 것은 제외). */
    private suspend fun manualMotionStarts() {
        controller.status
            .map { it.activeMotion?.source == CommandSource.MANUAL }
            .distinctUntilChanged()
            .dropWhile { it }
            .first { it }
    }

    private fun supersedeWaiting() {
        synchronized(waitingLock) {
            waiting?.superseded?.complete(Unit)
            waiting = null
        }
    }

    private suspend fun execute(command: RemoteCommand): RemoteResult {
        val result =
            when (command) {
                is RemoteCommand.Preset -> controller.goToPreset(command.slot, CommandSource.REMOTE)
                RemoteCommand.Stop -> controller.stop(CommandSource.REMOTE)
                is RemoteCommand.Light -> controller.setLight(command.action, CommandSource.REMOTE)
            }
        return when (result) {
            is BedResult.Ok -> RemoteResult.Executed
            is BedResult.Err -> RemoteResult.Failed(result.error)
        }
    }

    private suspend fun record(
        request: RemoteRequest,
        result: RemoteResult,
    ) {
        executionLog.record(
            ExecutionEntry(
                at = clock.now(),
                source = CommandSource.REMOTE,
                channel = request.channel,
                requestId = request.requestId,
                action = request.command.describe(),
                result = result.describe(),
            ),
        )
    }
}

internal fun RemoteCommand.describe(): String =
    when (this) {
        is RemoteCommand.Preset -> "preset $slot"
        RemoteCommand.Stop -> "stop"
        is RemoteCommand.Light -> "light $action"
    }

internal fun RemoteResult.describe(): String =
    when (this) {
        RemoteResult.Executed -> "executed"
        is RemoteResult.Failed -> "failed:$error"
        is RemoteResult.Rejected -> "rejected:$reason"
    }
