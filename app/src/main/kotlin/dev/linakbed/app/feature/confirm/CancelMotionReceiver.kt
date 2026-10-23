package dev.linakbed.app.feature.confirm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** 실행 전 확인 알림의 "취소" 액션 (전체 화면을 띄울 수 없을 때의 대체 경로). */
@AndroidEntryPoint
class CancelMotionReceiver : BroadcastReceiver() {
    @Inject
    lateinit var registry: PendingMotionRegistry

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        registry.cancel(intent.getStringExtra(EXTRA_REQUEST_ID))
    }

    companion object {
        private const val EXTRA_REQUEST_ID = "request_id"

        fun intent(
            context: Context,
            requestId: String,
        ): Intent = Intent(context, CancelMotionReceiver::class.java).putExtra(EXTRA_REQUEST_ID, requestId)
    }
}
