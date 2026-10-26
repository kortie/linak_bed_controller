package dev.linakbed.app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.linakbed.app.shortcut.ShortcutForwarder
import dev.linakbed.domain.controller.ControllerEnvironment
import dev.linakbed.domain.port.RemoteRequestDispatcher
import dev.linakbed.domain.remote.RemoteCommandHandler
import kotlinx.coroutines.CoroutineScope

/** 앱 바로가기 채널 (ADR-011, S11). */
@Module
@InstallIn(SingletonComponent::class)
object ShortcutModule {
    @Provides
    fun provideShortcutForwarder(
        dispatcher: RemoteRequestDispatcher,
        handler: RemoteCommandHandler,
        environment: ControllerEnvironment,
        @ApplicationScope scope: CoroutineScope,
    ): ShortcutForwarder = ShortcutForwarder(dispatcher, handler, environment.clock, environment.logger, scope)
}
