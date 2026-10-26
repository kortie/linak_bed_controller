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
import dev.linakbed.domain.log.BedLogger
import dev.linakbed.domain.port.Clock
import dev.linakbed.domain.port.ExecutionLog
import dev.linakbed.domain.port.MotionConfirmation
import dev.linakbed.domain.port.NotificationChannelSettingsStore
import dev.linakbed.domain.port.PresetNameStore
import dev.linakbed.domain.port.RemoteRequestDispatcher
import dev.linakbed.domain.port.RemoteSettingsStore
import dev.linakbed.domain.remote.DefaultRemoteCommandHandler
import dev.linakbed.domain.remote.RemoteCommandHandler
import dev.linakbed.domain.remote.notification.NotificationChannel
import dev.linakbed.smartthings.NotificationBridge
import kotlinx.coroutines.CoroutineScope
import javax.inject.Singleton

/** SmartThings 루틴 알림 → 원격 명령 실행 경로 (ADR-002, S07). */
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

    @Provides
    @Singleton
    fun provideNotificationChannel(
        settings: NotificationChannelSettingsStore,
        presetNames: PresetNameStore,
        handler: RemoteCommandHandler,
    ): NotificationChannel = NotificationChannel(settings, presetNames, handler)

    @Provides
    @Singleton
    fun provideNotificationBridge(
        channel: NotificationChannel,
        dispatcher: RemoteRequestDispatcher,
        settings: NotificationChannelSettingsStore,
        @ApplicationScope scope: CoroutineScope,
        logger: BedLogger,
    ): NotificationBridge = NotificationBridge(channel, dispatcher, settings, scope, logger)
}
