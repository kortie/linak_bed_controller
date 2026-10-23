package dev.linakbed.app.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import dagger.hilt.android.AndroidEntryPoint
import dev.linakbed.app.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

/**
 * 원격 명령을 화면이 꺼진 상태에서도 실행하기 위한 포그라운드 서비스 (type connectedDevice).
 * 실행은 [ServiceRemoteRequestDispatcher]가 하고, 서비스는 프로세스를 살려 두는 역할만 한다. 일이 없으면 스스로 멈춘다.
 */
@AndroidEntryPoint
class BedForegroundService : Service() {
    @Inject
    lateinit var dispatcher: ServiceRemoteRequestDispatcher

    @Inject
    lateinit var notifications: BedNotifications

    @Inject
    @ApplicationScope
    lateinit var scope: CoroutineScope

    private val running = AtomicInteger(0)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        notifications.ensureChannels()
        startForeground(
            BedNotifications.ID_SERVICE,
            notifications.serviceNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
        )
        val requestId = intent?.getStringExtra(ServiceTask.EXTRA_REQUEST_ID)
        if (requestId == null) {
            stopIfIdle()
            return START_NOT_STICKY
        }
        running.incrementAndGet()
        scope.launch {
            try {
                dispatcher.execute(requestId)
            } finally {
                running.decrementAndGet()
                stopIfIdle()
            }
        }
        return START_NOT_STICKY
    }

    private fun stopIfIdle() {
        if (running.get() == 0) stopSelf()
    }
}
