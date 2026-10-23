package dev.linakbed.domain.port

import dev.linakbed.domain.remote.RemoteSettings
import kotlinx.coroutines.flow.Flow

interface RemoteSettingsStore {
    fun settings(): Flow<RemoteSettings>

    suspend fun update(transform: (RemoteSettings) -> RemoteSettings)
}
