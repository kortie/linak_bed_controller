package dev.linakbed.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import dev.linakbed.domain.port.RemoteSettingsStore
import dev.linakbed.domain.remote.RemoteSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlin.time.Duration.Companion.milliseconds

/** 원격 명령 공통 설정 (02 문서 A3·A4). 저장값이 없으면 [RemoteSettings] 기본값. */
class DataStoreRemoteSettingsStore(
    private val dataStore: DataStore<Preferences>,
) : RemoteSettingsStore {
    override fun settings(): Flow<RemoteSettings> = dataStore.data.map(::read)

    override suspend fun update(transform: (RemoteSettings) -> RemoteSettings) {
        val next = transform(settings().first())
        dataStore.edit { prefs ->
            prefs[ENABLED] = next.enabled
            prefs[APP_SHORTCUTS_ENABLED] = next.appShortcutsEnabled
            prefs[FRESHNESS_MS] = next.freshness.inWholeMilliseconds
            prefs[RATE_LIMIT] = next.rateLimitPerMinute
            prefs[PRE_EXECUTION_DELAY_MS] = next.preExecutionDelay.inWholeMilliseconds
            prefs[NOTIFY_ON_SUCCESS] = next.notifyOnSuccess
        }
    }

    private fun read(prefs: Preferences): RemoteSettings {
        val defaults = RemoteSettings()
        return RemoteSettings(
            enabled = prefs[ENABLED] ?: defaults.enabled,
            appShortcutsEnabled = prefs[APP_SHORTCUTS_ENABLED] ?: defaults.appShortcutsEnabled,
            freshness = prefs[FRESHNESS_MS]?.milliseconds ?: defaults.freshness,
            rateLimitPerMinute = prefs[RATE_LIMIT] ?: defaults.rateLimitPerMinute,
            preExecutionDelay = prefs[PRE_EXECUTION_DELAY_MS]?.milliseconds ?: defaults.preExecutionDelay,
            notifyOnSuccess = prefs[NOTIFY_ON_SUCCESS] ?: defaults.notifyOnSuccess,
        )
    }

    companion object {
        private const val FILE_NAME = "remote_settings"
        private val ENABLED = booleanPreferencesKey("enabled")
        private val APP_SHORTCUTS_ENABLED = booleanPreferencesKey("app_shortcuts_enabled")
        private val FRESHNESS_MS = longPreferencesKey("freshness_ms")
        private val RATE_LIMIT = intPreferencesKey("rate_limit_per_minute")
        private val PRE_EXECUTION_DELAY_MS = longPreferencesKey("pre_execution_delay_ms")
        private val NOTIFY_ON_SUCCESS = booleanPreferencesKey("notify_on_success")

        fun create(context: Context): DataStoreRemoteSettingsStore =
            DataStoreRemoteSettingsStore(PreferenceDataStoreFactory.create { context.preferencesDataStoreFile(FILE_NAME) })
    }
}
