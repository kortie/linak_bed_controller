package dev.linakbed.domain.port

import dev.linakbed.domain.remote.notification.NotificationChannelSettings
import kotlinx.coroutines.flow.Flow

interface NotificationChannelSettingsStore {
    fun settings(): Flow<NotificationChannelSettings>

    suspend fun update(transform: (NotificationChannelSettings) -> NotificationChannelSettings)
}
