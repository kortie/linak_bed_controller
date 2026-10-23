package dev.linakbed.app.simbed

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.linakbed.app.di.ApplicationScope
import dev.linakbed.ble.AndroidBedLink
import dev.linakbed.domain.log.BedLogger
import dev.linakbed.domain.port.BedLink
import dev.linakbed.protocol.ProtocolProfile
import kotlinx.coroutines.CoroutineScope
import javax.inject.Singleton

/** 디버그: 페어링 주소에 따라 실제 링크와 가상 침대를 고른다 (릴리스는 `BedLinkModule`). */
@Module
@InstallIn(SingletonComponent::class)
object SimBedModule {
    @Provides
    @Singleton
    fun provideSimulatedBedLink(
        @ApplicationScope scope: CoroutineScope,
        logger: BedLogger,
    ): SimulatedBedLink = SimulatedBedLink(ProtocolProfile.DEFAULT, scope, logger)

    @Provides
    @Singleton
    fun provideBedLink(
        real: AndroidBedLink,
        simulated: SimulatedBedLink,
        @ApplicationScope scope: CoroutineScope,
    ): BedLink = SwitchingBedLink(real, simulated, scope)
}
