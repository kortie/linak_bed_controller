package dev.linakbed.app.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.linakbed.app.feature.confirm.AndroidMotionConfirmation
import dev.linakbed.app.feature.confirm.PendingMotionRegistry
import dev.linakbed.app.service.BedNotifications
import dev.linakbed.app.service.RemoteResultNotifier
import dev.linakbed.app.service.ResultNotifier
import dev.linakbed.app.service.ServiceRemoteRequestDispatcher
import dev.linakbed.app.service.ServiceStarter
import dev.linakbed.app.service.ServiceTask
import dev.linakbed.domain.controller.BedActions
import dev.linakbed.domain.controller.ControllerEnvironment
import dev.linakbed.domain.port.Clock
import dev.linakbed.domain.port.ExecutionLog
import dev.linakbed.domain.port.MotionConfirmation
import dev.linakbed.domain.port.RemoteRequestDispatcher
import dev.linakbed.domain.port.RemoteSettingsStore
import dev.linakbed.domain.remote.DefaultRemoteCommandHandler
import dev.linakbed.domain.remote.RemoteCommandHandler
import javax.inject.Singleton

/** 원격 명령 실행 경로: 앱 바로가기(삼성 모드 및 루틴, ADR-011) → 수용 정책·실행 전 대기 → 서비스 실행 (ADR-013). */
@Module
@InstallIn(SingletonComponent::class)
object RemoteModule {
    @Provides
    @Singleton
    fun provideNotifications(
        @ApplicationContext context: Context,
    ): BedNotifications = BedNotifications(context)

    @Provides
    @Singleton
    fun providePendingMotionRegistry(): PendingMotionRegistry = PendingMotionRegistry()

    @Provides
    @Singleton
    fun provideMotionConfirmation(
        @ApplicationContext context: Context,
        registry: PendingMotionRegistry,
        notifications: BedNotifications,
        clock: Clock,
    ): MotionConfirmation = AndroidMotionConfirmation(context, registry, notifications, clock)

    @Provides
    @Singleton
    fun provideRemoteCommandHandler(
        controller: BedActions,
        executionLog: ExecutionLog,
        settings: RemoteSettingsStore,
        confirmation: MotionConfirmation,
        clock: Clock,
    ): RemoteCommandHandler = DefaultRemoteCommandHandler(controller, executionLog, settings, confirmation, clock)

    @Provides
    fun provideServiceStarter(
        @ApplicationContext context: Context,
    ): ServiceStarter = ServiceStarter { requestId -> context.startForegroundService(ServiceTask.runRemote(context, requestId)) }

    @Provides
    @Singleton
    fun provideResultNotifier(
        notifications: BedNotifications,
        settings: RemoteSettingsStore,
    ): ResultNotifier = RemoteResultNotifier(notifications, settings)

    @Provides
    @Singleton
    fun provideDispatcher(
        handler: RemoteCommandHandler,
        starter: ServiceStarter,
        notifier: ResultNotifier,
        executionLog: ExecutionLog,
        environment: ControllerEnvironment,
    ): ServiceRemoteRequestDispatcher =
        ServiceRemoteRequestDispatcher(
            handler = handler,
            starter = starter,
            notifier = notifier,
            executionLog = executionLog,
            clock = environment.clock,
            logger = environment.logger,
        )

    @Provides
    fun provideRemoteRequestDispatcher(dispatcher: ServiceRemoteRequestDispatcher): RemoteRequestDispatcher = dispatcher
}
