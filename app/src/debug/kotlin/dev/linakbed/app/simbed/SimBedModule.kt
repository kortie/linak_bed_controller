package dev.linakbed.app.simbed

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.linakbed.app.di.ApplicationScope
import dev.linakbed.app.di.BedLinkFactory
import dev.linakbed.app.di.newAndroidBedLink
import dev.linakbed.domain.log.BedLogger
import kotlinx.coroutines.CoroutineScope
import javax.inject.Singleton

/** 디버그: 가상 침대 주소면 가상 링크, 아니면 실제 BLE (릴리스는 `BedLinkModule`). */
@Module
@InstallIn(SingletonComponent::class)
object SimBedModule {
    @Provides
    @Singleton
    fun provideSimulatedBeds(
        @ApplicationScope scope: CoroutineScope,
        logger: BedLogger,
    ): SimulatedBeds = SimulatedBeds(scope, logger)

    @Provides
    fun provideBedLinkFactory(
        @ApplicationContext context: Context,
        simulated: SimulatedBeds,
        logger: BedLogger,
        @ApplicationScope scope: CoroutineScope,
    ): BedLinkFactory =
        BedLinkFactory { device ->
            if (SimulatedBeds.isSimulated(device.address)) simulated.link(device.address) else newAndroidBedLink(context, logger, scope)
        }
}
