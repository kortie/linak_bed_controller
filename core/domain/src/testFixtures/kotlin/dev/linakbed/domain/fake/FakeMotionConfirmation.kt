package dev.linakbed.domain.fake

import dev.linakbed.domain.port.MotionConfirmation
import dev.linakbed.domain.port.PendingConfirmation
import dev.linakbed.domain.remote.RemoteRequest
import kotlinx.coroutines.CompletableDeferred
import kotlin.time.Duration

/** 띄운 취소 화면을 기록하고, 테스트에서 사용자 취소를 흉내 낸다. */
class FakeMotionConfirmation : MotionConfirmation {
    inner class Shown(
        val request: RemoteRequest,
        val delay: Duration,
    ) : PendingConfirmation {
        private val cancelled = CompletableDeferred<Unit>()
        var dismissed = false
            private set

        fun cancelByUser() {
            cancelled.complete(Unit)
        }

        override suspend fun awaitCancel() = cancelled.await()

        override fun dismiss() {
            dismissed = true
        }
    }

    val shown = mutableListOf<Shown>()

    override fun show(
        request: RemoteRequest,
        delay: Duration,
    ): PendingConfirmation = Shown(request, delay).also { shown += it }
}
