package dev.linakbed.app.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.linakbed.ble.AndroidBedLink
import dev.linakbed.ble.AndroidBleEnvironment
import dev.linakbed.ble.AndroidGattConnector
import dev.linakbed.ble.DiagnosticBedLink
import dev.linakbed.domain.log.BedLogger
import dev.linakbed.protocol.ProtocolProfile
import kotlinx.coroutines.CoroutineScope
import javax.inject.Singleton

/** 침대 연결은 앱 전체에서 하나 (A2: BLE 단일 소유자). `BedLink` 바인딩은 빌드 종류별 모듈(release `BedLinkModule`, debug `SimBedModule`)에 있다. */
@Module
@InstallIn(SingletonComponent::class)
object BleModule {
    @Provides
    @Singleton
    fun provideAndroidBedLink(
        @ApplicationContext context: Context,
        logger: BedLogger,
        @ApplicationScope scope: CoroutineScope,
    ): AndroidBedLink =
        AndroidBedLink(
            connector = AndroidGattConnector(context),
            environment = AndroidBleEnvironment(context),
            profile = ProtocolProfile.DEFAULT,
            logger = logger,
            scope = scope,
        )

    /** Protocol Lab(디버그) 전용. 정식 화면은 BedController를 거친다. */
    @Provides
    fun provideDiagnosticBedLink(link: AndroidBedLink): DiagnosticBedLink = link
}
