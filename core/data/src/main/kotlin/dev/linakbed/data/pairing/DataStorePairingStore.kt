package dev.linakbed.data.pairing

import android.content.Context
import androidx.datastore.core.DataStore
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** 페어링한 침대를 Preferences DataStore에 저장한다. */
class DataStorePairingStore(
    private val dataStore: DataStore<Preferences>,
) : PairingStore {
    override suspend fun pairedDevice(): PairedDevice? = observe().first()

    override fun observe(): Flow<PairedDevice?> =
        dataStore.data
            .map { prefs ->
                val bedId = prefs[BED_ID]
                val address = prefs[ADDRESS]
                if (bedId == null || address == null) {
                    null
                } else {
                    PairedDevice(bedId = bedId, address = address, name = prefs[NAME], cdmAssociationId = prefs[CDM_ID] ?: NO_ASSOCIATION)
                }
            }.distinctUntilChanged()

    override suspend fun save(device: PairedDevice?) {
        dataStore.edit { prefs ->
            prefs.clear()
            if (device != null) {
                prefs[BED_ID] = device.bedId
                prefs[ADDRESS] = device.address
                device.name?.let { prefs[NAME] = it }
                prefs[CDM_ID] = device.cdmAssociationId
            }
        }
    }

    companion object {
        private const val FILE_NAME = "pairing"
        private const val NO_ASSOCIATION = -1
        private val BED_ID = stringPreferencesKey("bed_id")
        private val ADDRESS = stringPreferencesKey("address")
        private val NAME = stringPreferencesKey("name")
        private val CDM_ID = intPreferencesKey("cdm_association_id")

        /** 앱 전체에서 한 번만 만든다 (같은 파일에 DataStore가 둘이면 안 됨 — DI에서 싱글턴). */
        fun create(context: Context): DataStorePairingStore =
            DataStorePairingStore(PreferenceDataStoreFactory.create { context.preferencesDataStoreFile(FILE_NAME) })
    }
}
