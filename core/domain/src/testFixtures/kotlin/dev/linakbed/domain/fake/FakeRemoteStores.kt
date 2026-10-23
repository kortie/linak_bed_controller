package dev.linakbed.domain.fake

import dev.linakbed.domain.port.NotificationChannelSettingsStore
import dev.linakbed.domain.port.RemoteSettingsStore
import dev.linakbed.domain.remote.RemoteSettings
import dev.linakbed.domain.remote.notification.NotificationChannelSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class FakeRemoteSettingsStore(
    initial: RemoteSettings = RemoteSettings(enabled = true),
) : RemoteSettingsStore {
    val current = MutableStateFlow(initial)

    override fun settings(): Flow<RemoteSettings> = current

    override suspend fun update(transform: (RemoteSettings) -> RemoteSettings) = current.update(transform)
}

class FakeNotificationChannelSettingsStore(
    initial: NotificationChannelSettings = NotificationChannelSettings(),
) : NotificationChannelSettingsStore {
    val current = MutableStateFlow(initial)

    override fun settings(): Flow<NotificationChannelSettings> = current

    override suspend fun update(transform: (NotificationChannelSettings) -> NotificationChannelSettings) = current.update(transform)
}
