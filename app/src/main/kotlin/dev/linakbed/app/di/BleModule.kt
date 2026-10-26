package dev.linakbed.app.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.linakbed.ble.AndroidBedLink
import dev.linakbed.ble.DiagnosticBedLink
import dev.linakbed.domain.log.BedLogger
import kotlinx.coroutines.CoroutineScope
import javax.inject.Singleton

/** Protocol Lab(디버그)용 원시 BLE 링크. 침대별 링크는 `BedLinkFactory`(release `BedLinkModule`, debug `SimBedModule`)가 만든다 (ADR-012). */
@Module
@InstallIn(SingletonComponent::class)
object BleModule {
    @Provides
    @Singleton
    fun provideAndroidBedLink(
        @ApplicationContext context: Context,
        logger: BedLogger,
        @ApplicationScope scope: CoroutineScope,
    ): AndroidBedLink = newAndroidBedLink(context, logger, scope)

    /** Protocol Lab(디버그) 전용. 정식 화면은 BedController를 거친다. */
    @Provides
    fun provideDiagnosticBedLink(link: AndroidBedLink): DiagnosticBedLink = link
}
