package dev.linakbed.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import dev.linakbed.app.log.CrashLogger
import dev.linakbed.domain.log.BedLogger
import dev.linakbed.domain.log.info
import dev.linakbed.smartthings.NotificationBridge
import dev.linakbed.smartthings.NotificationBridgeProvider
import javax.inject.Inject

@HiltAndroidApp
class LinakBedApp :
    Application(),
    NotificationBridgeProvider {
    @Inject
    lateinit var logger: BedLogger

    @Inject
    lateinit var crashLogger: CrashLogger

    /** 알림 리스너(integration:smartthings)가 가져다 쓴다 — 라이브러리 모듈에 Hilt를 넣지 않기 위함. */
    @Inject
    override lateinit var notificationBridge: NotificationBridge

    override fun onCreate() {
        super.onCreate()
        crashLogger.install()
        logger.info(AREA, "started version=${BuildConfig.VERSION_NAME} debug=${BuildConfig.DEBUG}")
    }

    private companion object {
        const val AREA = "App"
    }
}
