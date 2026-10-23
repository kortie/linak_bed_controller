package dev.linakbed.app.service

import android.content.Context
import android.content.Intent
import dev.linakbed.domain.remote.RemoteOutcome
import dev.linakbed.domain.remote.RemoteRequest

/** [BedForegroundService]에 보내는 작업. 요청 자체는 디스패처가 들고 있고 인텐트에는 requestId만 싣는다. */
object ServiceTask {
    const val ACTION_RUN_REMOTE = "com.juanstudio.linakbed.action.RUN_REMOTE"
    const val EXTRA_REQUEST_ID = "request_id"

    fun runRemote(
        context: Context,
        requestId: String,
    ): Intent = Intent(context, BedForegroundService::class.java).setAction(ACTION_RUN_REMOTE).putExtra(EXTRA_REQUEST_ID, requestId)
}

/** 포그라운드 서비스 시작. 백그라운드 제한에 걸리면 `ForegroundServiceStartNotAllowedException`을 던진다. */
fun interface ServiceStarter {
    fun startRemote(requestId: String)
}

/** 원격 요청 결과를 사용자에게 알린다. */
fun interface ResultNotifier {
    suspend fun onOutcome(
        request: RemoteRequest,
        outcome: RemoteOutcome,
    )
}
