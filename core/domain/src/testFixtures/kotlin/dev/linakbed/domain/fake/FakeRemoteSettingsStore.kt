package dev.linakbed.domain.fake

import dev.linakbed.domain.port.RemoteSettingsStore
import dev.linakbed.domain.remote.RemoteSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class FakeRemoteSettingsStore(
    initial: RemoteSettings = RemoteSettings(),
) : RemoteSettingsStore {
    val current = MutableStateFlow(initial)

    override fun settings(): Flow<RemoteSettings> = current

    override suspend fun update(transform: (RemoteSettings) -> RemoteSettings) = current.update(transform)
}
