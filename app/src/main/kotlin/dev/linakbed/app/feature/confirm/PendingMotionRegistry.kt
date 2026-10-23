package dev.linakbed.app.feature.confirm

import dev.linakbed.protocol.PresetSlot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant

/** 지금 떠 있는 실행 전 확인 (S9). 알림 액션·전체 화면·볼륨 키가 모두 여기로 취소를 알린다. 앱 전체에 하나. */
class PendingMotionRegistry {
    class Session(
        val requestId: String,
        val slot: PresetSlot?,
        val deadline: Instant,
    ) {
        val cancelled = CompletableDeferred<Unit>()
    }

    private val mutable = MutableStateFlow<Session?>(null)
    val current: StateFlow<Session?> = mutable.asStateFlow()

    fun begin(session: Session) {
        mutable.value = session
    }

    /** [requestId]가 null이면 지금 떠 있는 것을 취소. */
    fun cancel(requestId: String?) {
        val session = mutable.value ?: return
        if (requestId == null || session.requestId == requestId) session.cancelled.complete(Unit)
    }

    fun end(requestId: String) {
        mutable.compareAndSet(mutable.value?.takeIf { it.requestId == requestId } ?: return, null)
    }
}
