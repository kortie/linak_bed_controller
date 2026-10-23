package dev.linakbed.app.service

import android.app.ForegroundServiceStartNotAllowedException
import dev.linakbed.domain.BedError
import dev.linakbed.domain.controller.CommandSource
import dev.linakbed.domain.log.BedLogger
import dev.linakbed.domain.log.ExecutionEntry
import dev.linakbed.domain.log.warn
import dev.linakbed.domain.port.Clock
import dev.linakbed.domain.port.ExecutionLog
import dev.linakbed.domain.port.RemoteRequestDispatcher
import dev.linakbed.domain.remote.RemoteCommandHandler
import dev.linakbed.domain.remote.RemoteOutcome
import dev.linakbed.domain.remote.RemoteRequest
import dev.linakbed.domain.remote.RemoteResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * [RemoteRequestDispatcher] 구현: 포그라운드 서비스를 띄워 그 안에서 핸들러를 실행하고 결과를 기다린다.
 * 백그라운드 시작이 막히면(CDM 예외가 적용되지 않는 등) 앱 스코프에서 직접 실행하는 폴백으로 간다.
 * [RemoteRequest]만 알고 채널은 모른다 (ADR-006).
 */
class ServiceRemoteRequestDispatcher(
    private val handler: RemoteCommandHandler,
    private val starter: ServiceStarter,
    private val notifier: ResultNotifier,
    private val executionLog: ExecutionLog,
    private val clock: Clock,
    private val logger: BedLogger,
) : RemoteRequestDispatcher {
    private class Pending(
        val request: RemoteRequest,
        val outcome: CompletableDeferred<RemoteOutcome>,
    )

    private val pending = ConcurrentHashMap<String, Pending>()
    private val mutableLastFallback = MutableStateFlow<Instant?>(null)

    /** 마지막으로 폴백 경로를 쓴 시각 (진단 화면 경고용). */
    val lastFallback: StateFlow<Instant?> = mutableLastFallback.asStateFlow()

    override suspend fun dispatch(request: RemoteRequest): RemoteOutcome {
        val entry = Pending(request, CompletableDeferred())
        pending[request.requestId] = entry
        return try {
            starter.startRemote(request.requestId)
            withTimeoutOrNull(MAX_EXECUTION) { entry.outcome.await() } ?: timedOut(request)
        } catch (
            @Suppress("SwallowedException") denied: ForegroundServiceStartNotAllowedException,
        ) {
            pending.remove(request.requestId)
            fallback(request, denied.message)
        }
    }

    /** 서비스가 부른다. 등록된 요청이 없으면 null. */
    suspend fun execute(requestId: String): RemoteOutcome? {
        val entry = pending.remove(requestId) ?: return null
        val outcome = run(entry.request)
        entry.outcome.complete(outcome)
        return outcome
    }

    private suspend fun fallback(
        request: RemoteRequest,
        reason: String?,
    ): RemoteOutcome {
        logger.warn(AREA, "foreground service start denied, running ${request.requestId} in place: $reason")
        mutableLastFallback.value = clock.now()
        executionLog.record(
            ExecutionEntry(
                at = clock.now(),
                source = CommandSource.REMOTE,
                channel = request.channel,
                requestId = request.requestId,
                action = "dispatch",
                result = "fallback",
                detail = reason,
            ),
        )
        return withTimeoutOrNull(MAX_EXECUTION) { run(request) } ?: timedOut(request)
    }

    private suspend fun run(request: RemoteRequest): RemoteOutcome {
        val outcome = handler.handle(request)
        notifier.onOutcome(request, outcome)
        return outcome
    }

    private fun timedOut(request: RemoteRequest): RemoteOutcome {
        pending.remove(request.requestId)
        logger.warn(AREA, "${request.requestId} did not finish within $MAX_EXECUTION")
        return RemoteOutcome(request.requestId, RemoteResult.Failed(BedError.Interrupted(CommandSource.REMOTE)))
    }

    companion object {
        private const val AREA = "Service"

        /**
         * 멈춤 방지 상한. 정상 최대치(대기 60s + 연결 2×10s + 프리셋 60s ≈ 140s)보다 넉넉하게.
         * 모터 자체는 이와 별개로 S1(60s)이 멈춘다.
         */
        val MAX_EXECUTION: Duration = 5.minutes
    }
}
