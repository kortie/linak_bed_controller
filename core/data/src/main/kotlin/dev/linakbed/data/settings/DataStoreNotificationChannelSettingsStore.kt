package dev.linakbed.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import dev.linakbed.domain.port.NotificationChannelSettingsStore
import dev.linakbed.domain.remote.notification.NotificationChannelSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** SmartThings 알림 채널 설정 (02 문서 Part B). */
class DataStoreNotificationChannelSettingsStore(
    private val dataStore: DataStore<Preferences>,
) : NotificationChannelSettingsStore {
    override fun settings(): Flow<NotificationChannelSettings> = dataStore.data.map(::read)

    override suspend fun update(transform: (NotificationChannelSettings) -> NotificationChannelSettings) {
        val next = transform(settings().first())
        dataStore.edit { prefs ->
            prefs[PREFIX] = next.prefix
            val pin = next.pin
            if (pin == null) prefs.remove(PIN) else prefs[PIN] = pin
            prefs[ALLOWED_PACKAGES] = next.allowedPackages
            prefs[DISMISS_HANDLED] = next.dismissHandledNotification
        }
    }

    private fun read(prefs: Preferences): NotificationChannelSettings {
        val defaults = NotificationChannelSettings()
        return NotificationChannelSettings(
            prefix = prefs[PREFIX] ?: defaults.prefix,
            pin = prefs[PIN],
            allowedPackages = prefs[ALLOWED_PACKAGES] ?: defaults.allowedPackages,
            dismissHandledNotification = prefs[DISMISS_HANDLED] ?: defaults.dismissHandledNotification,
        )
    }

    companion object {
        private const val FILE_NAME = "notification_channel_settings"
        private val PREFIX = stringPreferencesKey("prefix")
        private val PIN = stringPreferencesKey("pin")
        private val ALLOWED_PACKAGES = stringSetPreferencesKey("allowed_packages")
        private val DISMISS_HANDLED = booleanPreferencesKey("dismiss_handled_notification")

        fun create(context: Context): DataStoreNotificationChannelSettingsStore =
            DataStoreNotificationChannelSettingsStore(PreferenceDataStoreFactory.create { context.preferencesDataStoreFile(FILE_NAME) })
    }
}
