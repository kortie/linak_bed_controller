package dev.linakbed.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import dev.linakbed.app.log.CrashLogger
import dev.linakbed.domain.log.BedLogger
import dev.linakbed.domain.log.info
import javax.inject.Inject

@HiltAndroidApp
class LinakBedApp : Application() {
    @Inject
    lateinit var logger: BedLogger

    @Inject
    lateinit var crashLogger: CrashLogger

    override fun onCreate() {
        super.onCreate()
        crashLogger.install()
        logger.info(AREA, "started version=${BuildConfig.VERSION_NAME} debug=${BuildConfig.DEBUG}")
    }

    private companion object {
        const val AREA = "App"
    }
}
