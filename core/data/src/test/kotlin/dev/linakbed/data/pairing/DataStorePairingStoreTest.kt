package dev.linakbed.data.pairing

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.linakbed.domain.model.PairedDevice
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File

/** ADR-012: 침대 목록 (최대 2), 단일 저장값 이전. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class) // org.json은 Robolectric에서만 실제 구현
class DataStorePairingStoreTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val bed1 = PairedDevice(bedId = "bed-1", address = "AA:BB:CC:DD:EE:FF", name = "Linak", cdmAssociationId = 7)
    private val bed2 = PairedDevice(bedId = "bed-2", address = "11:22:33:44:55:66", name = null, cdmAssociationId = -1)
    private val file get() = File(temp.root, "pairing.preferences_pb")

    private fun TestScope.dataStore() = PreferenceDataStoreFactory.create(scope = backgroundScope) { file }

    @Test
    fun `empty store has no beds`() =
        runTest {
            assertThat(DataStorePairingStore(dataStore()).all()).isEmpty()
        }

    @Test
    fun `adds up to two beds with every field, a name-less bed round-trips`() =
        runTest {
            val store = DataStorePairingStore(dataStore())

            assertThat(store.save(bed1)).isTrue()
            assertThat(store.save(bed2)).isTrue()
            assertThat(store.save(PairedDevice("bed-3", "77:77:77:77:77:77", "x", 3))).isFalse()

            assertThat(store.all()).containsExactly(bed1, bed2).inOrder()
        }

    @Test
    fun `saving the same bed or address replaces it, remove deletes one`() =
        runTest {
            val store = DataStorePairingStore(dataStore())
            store.save(bed1)
            store.save(bed2)

            store.save(bed1.copy(name = "안방"))
            store.save(bed2.copy(bedId = "bed-2b", address = bed2.address.lowercase()))
            store.remove("bed-1")

            assertThat(store.all()).containsExactly(bed2.copy(bedId = "bed-2b", address = bed2.address.lowercase()))
        }

    @Test
    fun `a single bed saved by an older version is read and migrated`() =
        runTest {
            val ds = dataStore()
            ds.edit {
                it[stringPreferencesKey("bed_id")] = "old"
                it[stringPreferencesKey("address")] = "AA:AA:AA:AA:AA:AA"
                it[stringPreferencesKey("name")] = "예전 침대"
                it[intPreferencesKey("cdm_association_id")] = 4
            }
            val store = DataStorePairingStore(ds)
            val old = PairedDevice("old", "AA:AA:AA:AA:AA:AA", "예전 침대", 4)
            assertThat(store.all()).containsExactly(old)

            store.save(bed2)

            assertThat(store.all()).containsExactly(old, bed2).inOrder()
        }

    @Test
    fun `observeAll emits every change`() =
        runTest {
            val store = DataStorePairingStore(dataStore())
            val seen = mutableListOf<List<PairedDevice>>()
            val job = backgroundScope.launch { store.observeAll().collect { seen += it } }
            runCurrent()

            store.save(bed1)
            runCurrent()
            store.remove("bed-1")
            runCurrent()

            assertThat(seen).containsExactly(emptyList<PairedDevice>(), listOf(bed1), emptyList<PairedDevice>()).inOrder()
            job.cancel()
        }
}
