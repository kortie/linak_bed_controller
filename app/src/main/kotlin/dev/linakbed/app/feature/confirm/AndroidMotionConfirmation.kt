package dev.linakbed.app.feature.confirm

import android.content.Context
import dev.linakbed.app.R
import dev.linakbed.app.service.BedNotifications
import dev.linakbed.domain.port.Clock
import dev.linakbed.domain.port.MotionConfirmation
import dev.linakbed.domain.port.PendingConfirmation
import dev.linakbed.domain.remote.RemoteCommand
import dev.linakbed.domain.remote.RemoteRequest
import kotlin.time.Duration
import kotlin.time.toJavaDuration

/** [MotionConfirmation] 구현 (ADR-008): 전체 화면 알림으로 큰 취소 화면을 띄운다. 대기 시간 측정은 도메인이 한다. */
class AndroidMotionConfirmation(
    private val context: Context,
    private val registry: PendingMotionRegistry,
    private val notifications: BedNotifications,
    private val clock: Clock,
) : MotionConfirmation {
    override fun show(
        request: RemoteRequest,
        delay: Duration,
    ): PendingConfirmation {
        val slot = (request.command as? RemoteCommand.Preset)?.slot
        val session = PendingMotionRegistry.Session(request.requestId, slot, clock.now().plus(delay.toJavaDuration()))
        registry.begin(session)
        val label =
            slot?.let { context.getString(R.string.preset_fallback_name, it.number) } ?: context.getString(R.string.app_label_launcher)
        notifications.showConfirmation(request.requestId, label)
        return object : PendingConfirmation {
            override suspend fun awaitCancel() = session.cancelled.await()

            override fun dismiss() {
                registry.end(request.requestId)
                notifications.cancelConfirmation()
            }
        }
    }
}
