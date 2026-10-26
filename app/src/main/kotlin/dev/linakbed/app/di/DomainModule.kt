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
import dev.linakbed.data.preset.DataStorePresetPoseStore
import dev.linakbed.data.settings.DataStoreRemoteSettingsStore
import dev.linakbed.domain.controller.BedActions
import dev.linakbed.domain.controller.BedControllerConfig
import dev.linakbed.domain.controller.BedFleet
import dev.linakbed.domain.controller.BedParts
import dev.linakbed.domain.controller.BedPartsFactory
import dev.linakbed.domain.controller.ControllerEnvironment
import dev.linakbed.domain.controller.DefaultBedController
import dev.linakbed.domain.log.BedLogger
import dev.linakbed.domain.port.Clock
import dev.linakbed.domain.port.ExecutionLog
import dev.linakbed.domain.port.PairingStore
import dev.linakbed.domain.port.PresetNameStore
import dev.linakbed.domain.port.PresetPoseStore
import dev.linakbed.domain.port.RemoteSettingsStore
import kotlinx.coroutines.CoroutineScope
import javax.inject.Singleton

/** 침대들(BedFleet)과 저장소 포트. */
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
    fun provideControllerEnvironment(
        clock: Clock,
        logger: BedLogger,
    ): ControllerEnvironment = ControllerEnvironment(clock, logger)

    /** 침대 한 대의 부품: 그 침대만의 링크와 컨트롤러(안전 감독·중재) (ADR-012). */
    @Provides
    fun provideBedPartsFactory(
        @ApplicationScope scope: CoroutineScope,
        links: BedLinkFactory,
        executionLog: ExecutionLog,
        environment: ControllerEnvironment,
    ): BedPartsFactory =
        BedPartsFactory { device, source ->
            val link = links.create(device)
            BedParts(link, DefaultBedController(scope, link, source, executionLog, BedControllerConfig(), environment))
        }

    /** 연결한 침대들: 침대마다 부품 + 프리셋 자세 기록 (ADR-012). */
    @Provides
    @Singleton
    fun provideBedFleet(
        @ApplicationScope scope: CoroutineScope,
        pairingStore: PairingStore,
        parts: BedPartsFactory,
        presetPoses: PresetPoseStore,
        environment: ControllerEnvironment,
    ): BedFleet = BedFleet(scope, pairingStore, parts, presetPoses, environment.logger)

    /** 원격 명령·전체 STOP: 모든 침대 (ADR-012). */
    @Provides
    fun provideAllBeds(fleet: BedFleet): BedActions = fleet.all

    @Provides
    @Singleton
    fun providePresetPoseStore(
        @ApplicationContext context: Context,
    ): PresetPoseStore = DataStorePresetPoseStore.create(context)
}
