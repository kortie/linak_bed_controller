package dev.linakbed.data.preset

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStoreFile
import dev.linakbed.domain.port.PresetPoseStore
import dev.linakbed.domain.preset.PresetPose
import dev.linakbed.protocol.PresetSlot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** 침대별·프리셋별 자세 (S12·S13). 머리·발 둘 다 있는 슬롯만 낸다. */
class DataStorePresetPoseStore(
    private val dataStore: DataStore<Preferences>,
) : PresetPoseStore {
    override fun poses(bedId: String): Flow<Map<PresetSlot, PresetPose>> =
        dataStore.data.map { prefs ->
            PresetSlot.entries
                .mapNotNull { slot ->
                    val head = prefs[headKey(bedId, slot)] ?: return@mapNotNull null
                    val feet = prefs[feetKey(bedId, slot)] ?: return@mapNotNull null
                    slot to PresetPose(head, feet)
                }.toMap()
        }

    override suspend fun record(
        bedId: String,
        slot: PresetSlot,
        pose: PresetPose,
    ) {
        dataStore.edit {
            it[headKey(bedId, slot)] = pose.headCm
            it[feetKey(bedId, slot)] = pose.feetCm
        }
    }

    private fun headKey(
        bedId: String,
        slot: PresetSlot,
    ) = doublePreferencesKey("$bedId:preset_${slot.number}_head_cm")

    private fun feetKey(
        bedId: String,
        slot: PresetSlot,
    ) = doublePreferencesKey("$bedId:preset_${slot.number}_feet_cm")

    companion object {
        private const val FILE_NAME = "preset_poses"

        /** 앱 전체에서 한 번만 만든다 (DI 싱글턴). */
        fun create(context: Context): DataStorePresetPoseStore =
            DataStorePresetPoseStore(PreferenceDataStoreFactory.create { context.preferencesDataStoreFile(FILE_NAME) })
    }
}
