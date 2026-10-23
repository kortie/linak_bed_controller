package dev.linakbed.app.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.linakbed.data.log.RoomExecutionLog
import dev.linakbed.data.pairing.DataStorePairingStore
import dev.linakbed.data.preset.DataStorePresetNameStore
import dev.linakbed.data.settings.DataStoreNotificationChannelSettingsStore
import dev.linakbed.data.settings.DataStoreRemoteSettingsStore
import dev.linakbed.domain.controller.BedController
import dev.linakbed.domain.controller.BedControllerConfig
import dev.linakbed.domain.controller.ControllerEnvironment
import dev.linakbed.domain.controller.DefaultBedController
import dev.linakbed.domain.log.BedLogger
import dev.linakbed.domain.port.BedLink
import dev.linakbed.domain.port.Clock
import dev.linakbed.domain.port.ExecutionLog
import dev.linakbed.domain.port.NotificationChannelSettingsStore
import dev.linakbed.domain.port.PairingStore
import dev.linakbed.domain.port.PresetNameStore
import dev.linakbed.domain.port.RemoteSettingsStore
import kotlinx.coroutines.CoroutineScope
import javax.inject.Singleton

/** 침대 제어의 유일한 진입점(BedController)과 그 저장소 포트. */
@Module
@InstallIn(SingletonComponent::class)
object DomainModule {
    @Provides
    @Singleton
    fun providePairingStore(
        @ApplicationContext context: Context,
    ): PairingStore = DataStorePairingStore.create(context)

    @Provides
    @Singleton
    fun providePresetNameStore(
        @ApplicationContext context: Context,
    ): PresetNameStore = DataStorePresetNameStore.create(context)

    @Provides
    @Singleton
    fun provideExecutionLog(
        @ApplicationContext context: Context,
    ): ExecutionLog = RoomExecutionLog.create(context)

    @Provides
    @Singleton
    fun provideRemoteSettingsStore(
        @ApplicationContext context: Context,
    ): RemoteSettingsStore = DataStoreRemoteSettingsStore.create(context)

    @Provides
    @Singleton
    fun provideNotificationChannelSettingsStore(
        @ApplicationContext context: Context,
    ): NotificationChannelSettingsStore = DataStoreNotificationChannelSettingsStore.create(context)

    @Provides
    fun provideControllerEnvironment(
        clock: Clock,
        logger: BedLogger,
    ): ControllerEnvironment = ControllerEnvironment(clock, logger)

    @Provides
    @Singleton
    fun provideBedController(
        @ApplicationScope scope: CoroutineScope,
        link: BedLink,
        pairingStore: PairingStore,
        executionLog: ExecutionLog,
        environment: ControllerEnvironment,
    ): BedController =
        DefaultBedController(
            scope = scope,
            link = link,
            pairingStore = pairingStore,
            executionLog = executionLog,
            config = BedControllerConfig(),
            environment = environment,
        )
}
