package dev.linakbed.app.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.linakbed.app.BuildConfig
import dev.linakbed.app.log.AndroidBedLogger
import dev.linakbed.app.log.CrashLogger
import dev.linakbed.app.log.FileLogSink
import dev.linakbed.app.log.LogDirectory
import dev.linakbed.domain.log.BedLogger
import dev.linakbed.domain.log.LogLevel
import dev.linakbed.domain.port.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors
import javax.inject.Singleton

/** 로깅 (ADR-007): 파일 쓰기 전용 단일 스레드에서 Logcat + 파일로 기록한다. */
@Module
@InstallIn(SingletonComponent::class)
object LoggingModule {
    private const val LOG_THREAD_NAME = "linakbed-log"

    @Provides
    @Singleton
    fun provideAndroidBedLogger(
        @ApplicationContext context: Context,
        clock: Clock,
    ): AndroidBedLogger {
        val dispatcher =
            Executors
                .newSingleThreadExecutor { runnable ->
                    Thread(runnable, LOG_THREAD_NAME).apply { isDaemon = true }
                }.asCoroutineDispatcher()
        return AndroidBedLogger(
            sink = FileLogSink(LogDirectory.resolve(context), clock),
            minLevel = if (BuildConfig.DEBUG) LogLevel.DEBUG else LogLevel.INFO,
            clock = clock,
            scope = CoroutineScope(SupervisorJob() + dispatcher),
        )
    }

    @Provides
    fun provideBedLogger(logger: AndroidBedLogger): BedLogger = logger

    @Provides
    @Singleton
    fun provideCrashLogger(logger: AndroidBedLogger): CrashLogger = CrashLogger(logger)
}
