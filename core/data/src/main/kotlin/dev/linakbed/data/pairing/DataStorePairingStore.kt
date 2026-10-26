package dev.linakbed.data.pairing

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import dev.linakbed.domain.model.PairedDevice
import dev.linakbed.domain.port.PairingStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

/**
 * 연결한 침대 목록을 Preferences DataStore에 JSON 배열로 저장한다 (ADR-012).
 * S12까지의 단일 침대 저장값(`bed_id`·`address`…)이 남아 있으면 첫 침대로 읽고, 다음 저장 때 목록으로 옮긴다.
 */
class DataStorePairingStore(
    private val dataStore: DataStore<Preferences>,
) : PairingStore {
    override fun observeAll(): Flow<List<PairedDevice>> = dataStore.data.map(::read).distinctUntilChanged()

    override suspend fun save(device: PairedDevice): Boolean {
        var saved = false
        dataStore.edit { prefs ->
            val merged = PairingStore.merged(read(prefs), device) ?: return@edit
            write(prefs, merged)
            saved = true
        }
        return saved
    }

    override suspend fun remove(bedId: String) {
        dataStore.edit { prefs -> write(prefs, read(prefs).filterNot { it.bedId == bedId }) }
    }

    private fun read(prefs: Preferences): List<PairedDevice> {
        val json = prefs[BEDS] ?: return listOfNotNull(legacy(prefs))
        val array = JSONArray(json)
        return (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            PairedDevice(
                bedId = o.getString(KEY_ID),
                address = o.getString(KEY_ADDRESS),
                name = if (o.isNull(KEY_NAME)) null else o.optString(KEY_NAME),
                cdmAssociationId = o.optInt(KEY_CDM, NO_ASSOCIATION),
            )
        }
    }

    private fun write(
        prefs: MutablePreferences,
        devices: List<PairedDevice>,
    ) {
        LEGACY_KEYS.forEach { prefs.remove(it) }
        prefs.remove(LEGACY_CDM_ID)
        val array = JSONArray()
        devices.forEach { d ->
            array.put(
                JSONObject()
                    .put(KEY_ID, d.bedId)
                    .put(KEY_ADDRESS, d.address)
                    .put(KEY_NAME, d.name ?: JSONObject.NULL)
                    .put(KEY_CDM, d.cdmAssociationId),
            )
        }
        prefs[BEDS] = array.toString()
    }

    private fun legacy(prefs: Preferences): PairedDevice? {
        val bedId = prefs[LEGACY_BED_ID]
        val address = prefs[LEGACY_ADDRESS]
        return if (bedId == null ||
            address == null
        ) {
            null
        } else {
            PairedDevice(bedId, address, prefs[LEGACY_NAME], prefs[LEGACY_CDM_ID] ?: NO_ASSOCIATION)
        }
    }

    companion object {
        private const val FILE_NAME = "pairing"
        private const val NO_ASSOCIATION = -1
        private const val KEY_ID = "bedId"
        private const val KEY_ADDRESS = "address"
        private const val KEY_NAME = "name"
        private const val KEY_CDM = "cdm"
        private val BEDS = stringPreferencesKey("beds")
        private val LEGACY_BED_ID = stringPreferencesKey("bed_id")
        private val LEGACY_ADDRESS = stringPreferencesKey("address")
        private val LEGACY_NAME = stringPreferencesKey("name")
        private val LEGACY_CDM_ID = intPreferencesKey("cdm_association_id")
        private val LEGACY_KEYS = listOf(LEGACY_BED_ID, LEGACY_ADDRESS, LEGACY_NAME)

        /** 앱 전체에서 한 번만 만든다 (같은 파일에 DataStore가 둘이면 안 됨 — DI에서 싱글턴). */
        fun create(context: Context): DataStorePairingStore =
            DataStorePairingStore(PreferenceDataStoreFactory.create { context.preferencesDataStoreFile(FILE_NAME) })
    }
}
