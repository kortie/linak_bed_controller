package dev.linakbed.app.shortcut

import android.net.Uri
import dev.linakbed.domain.log.BedLogger
import dev.linakbed.domain.log.info
import dev.linakbed.domain.port.Clock
import dev.linakbed.domain.port.RemoteRequestDispatcher
import dev.linakbed.domain.remote.RejectReason
import dev.linakbed.domain.remote.RemoteChannel
import dev.linakbed.domain.remote.RemoteCommandHandler
import dev.linakbed.domain.remote.RemoteRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import java.security.SecureRandom

/**
 * 바로가기 URI → 원격 요청 (02 문서 Part C2). 해석에 실패하면 `INVALID`로 기록만 한다.
 * 이후 수용 정책·실행 전 대기·실행은 원격 공통 경로가 맡는다.
 */
class ShortcutForwarder(
    private val dispatcher: RemoteRequestDispatcher,
    private val handler: RemoteCommandHandler,
    private val clock: Clock,
    private val logger: BedLogger,
    private val scope: CoroutineScope,
    private val newRequestId: () -> String = ::randomRequestId,
) {
    fun forward(uri: Uri?) {
        val requestId = newRequestId()
        val command = ShortcutCommands.parse(uri)
        if (command == null) {
            scope.launch { handler.reject(RemoteChannel.APP_SHORTCUT, requestId, RejectReason.INVALID, "unknown shortcut: $uri") }
            return
        }
        logger.info(AREA, "$requestId shortcut → $command")
        val request = RemoteRequest(requestId, RemoteChannel.APP_SHORTCUT, command, receivedAt = clock.now(), sentAt = null)
        // 포그라운드 서비스 시작은 중계 화면이 떠 있는 동안 해야 백그라운드 제한에 걸리지 않는다:
        // UNDISPATCHED로 시작해 서비스 시작 호출까지 호출한 스레드(onCreate)에서 실행하고, 결과 대기만 이어서 한다.
        scope.launch(start = CoroutineStart.UNDISPATCHED) { dispatcher.dispatch(request) }
    }

    private companion object {
        const val AREA = "Remote"
        const val REQUEST_ID_BYTES = 8
        val random = SecureRandom()

        fun randomRequestId(): String = ByteArray(REQUEST_ID_BYTES).also(random::nextBytes).joinToString("") { "%02x".format(it) }
    }
}
