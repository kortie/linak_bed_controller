package dev.linakbed.data.pairing

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
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
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class DataStorePairingStoreTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val device = PairedDevice(bedId = "bed-1", address = "AA:BB:CC:DD:EE:FF", name = "Linak", cdmAssociationId = 7)

    private fun TestScope.store(file: File = File(temp.root, "pairing.preferences_pb")) =
        DataStorePairingStore(PreferenceDataStoreFactory.create(scope = backgroundScope) { file })

    @Test
    fun `empty store has no paired device`() =
        runTest {
            assertThat(store().pairedDevice()).isNull()
        }

    @Test
    fun `saves and loads every field`() =
        runTest {
            val store = store()

            store.save(device)

            assertThat(store.pairedDevice()).isEqualTo(device)
        }

    @Test
    fun `a device without a name round-trips`() =
        runTest {
            val store = store()

            store.save(device.copy(name = null))

            assertThat(store.pairedDevice()).isEqualTo(device.copy(name = null))
        }

    @Test
    fun `saving another device replaces the old one, null clears`() =
        runTest {
            val store = store()
            store.save(device)

            store.save(device.copy(bedId = "bed-2", address = "11:22:33:44:55:66", name = null))
            assertThat(store.pairedDevice()?.bedId).isEqualTo("bed-2")
            assertThat(store.pairedDevice()?.name).isNull()

            store.save(null)
            assertThat(store.pairedDevice()).isNull()
        }

    @Test
    fun `observe emits the current device and every change`() =
        runTest {
            val store = store()
            val seen = mutableListOf<PairedDevice?>()
            val job = backgroundScope.launch { store.observe().collect { seen += it } }
            runCurrent()

            store.save(device)
            runCurrent()
            store.save(null)
            runCurrent()

            assertThat(seen).containsExactly(null, device, null).inOrder()
            job.cancel()
        }
}
