package dev.linakbed.data.preset

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import dev.linakbed.domain.port.PresetNameStore
import dev.linakbed.domain.preset.PresetName
import dev.linakbed.protocol.PresetSlot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** 프리셋 이름 저장. 검증은 호출 쪽이 `PresetName.validate()`로 먼저 한다. */
class DataStorePresetNameStore(
    private val dataStore: DataStore<Preferences>,
) : PresetNameStore {
    override fun names(): Flow<Map<PresetSlot, String>> =
        dataStore.data.map { prefs -> PresetSlot.entries.associateWith { prefs[key(it)] ?: PresetName.defaultFor(it) } }

    override suspend fun rename(
        slot: PresetSlot,
        name: String,
    ) {
        dataStore.edit { it[key(slot)] = name.trim() }
    }

    private fun key(slot: PresetSlot) = stringPreferencesKey("preset_${slot.number}_name")

    companion object {
        private const val FILE_NAME = "preset_names"

        /** 앱 전체에서 한 번만 만든다 (DI 싱글턴). */
        fun create(context: Context): DataStorePresetNameStore =
            DataStorePresetNameStore(PreferenceDataStoreFactory.create { context.preferencesDataStoreFile(FILE_NAME) })
    }
}
