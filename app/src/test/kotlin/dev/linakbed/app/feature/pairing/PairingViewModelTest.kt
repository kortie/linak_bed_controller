package dev.linakbed.app.feature.pairing

import android.app.PendingIntent
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.linakbed.app.pairing.AssociationEvent
import dev.linakbed.app.pairing.IdGenerator
import dev.linakbed.app.pairing.PairingFilter
import dev.linakbed.domain.fake.FakeBedLink
import dev.linakbed.domain.fake.FakePairingStore
import dev.linakbed.domain.model.PairedDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** IntentSender를 만들려고 Robolectric으로 돌린다. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class PairingViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val pairing = FakeCompanionPairing()
    private val store = FakePairingStore(device = null)
    private val link = FakeBedLink()
    private var nextId = 0
    private val ids = IdGenerator { "bed-${++nextId}" }

    private fun viewModel() = PairingViewModel(pairing, store, link, ids)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `loads the saved pairing`() =
        runTest(dispatcher) {
            store.device = FakePairingStore.DEFAULT
            val vm = viewModel()
            runCurrent()

            assertThat(vm.uiState.value.loaded).isTrue()
            assertThat(vm.uiState.value.paired).isEqualTo(FakePairingStore.DEFAULT)
        }

    @Test
    fun `pending selection is handed to the screen once`() =
        runTest(dispatcher) {
            val vm = viewModel()
            val sender =
                PendingIntent
                    .getActivity(ApplicationProvider.getApplicationContext(), 0, Intent(), PendingIntent.FLAG_IMMUTABLE)
                    .intentSender

            vm.onAction(PairingAction.Pair)
            runCurrent()
            pairing.events.emit(AssociationEvent.Pending(sender))
            runCurrent()

            assertThat(vm.uiState.value.pendingSelection).isSameInstanceAs(sender)
            assertThat(vm.uiState.value.inProgress).isTrue()
            vm.onAction(PairingAction.SelectionLaunched)
            assertThat(vm.uiState.value.pendingSelection).isNull()
        }

    @Test
    fun `association creates a new bedId and saves the device`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.onAction(PairingAction.Pair)
            runCurrent()

            pairing.events.emit(AssociationEvent.Created(associationId = 5, address = "AA:BB:CC:DD:EE:FF", name = "DPG1M"))
            runCurrent()

            val expected = PairedDevice(bedId = "bed-1", address = "AA:BB:CC:DD:EE:FF", name = "DPG1M", cdmAssociationId = 5)
            assertThat(store.device).isEqualTo(expected)
            assertThat(vm.uiState.value.paired).isEqualTo(expected)
            assertThat(vm.uiState.value.inProgress).isFalse()
            assertThat(vm.uiState.value.message).isEqualTo(PairingMessage.Paired("DPG1M"))
            assertThat(pairing.requestedFilters).containsExactly(PairingFilter.ControlServiceUuid)
        }

    @Test
    fun `pairing the same bed again keeps its bedId`() =
        runTest(dispatcher) {
            store.device = PairedDevice("bed-original", "AA:BB:CC:DD:EE:FF", "old", 1)
            val vm = viewModel()
            runCurrent()
            vm.onAction(PairingAction.Pair)
            runCurrent()

            pairing.events.emit(AssociationEvent.Created(associationId = 9, address = "aa:bb:cc:dd:ee:ff", name = "DPG1M"))
            runCurrent()

            assertThat(store.device?.bedId).isEqualTo("bed-original")
            assertThat(store.device?.cdmAssociationId).isEqualTo(9)
            assertThat(pairing.disassociated).isEmpty()
            assertThat(link.disconnects).isEqualTo(0)
        }

    @Test
    fun `pairing a different bed releases the old association and connection`() =
        runTest(dispatcher) {
            store.device = PairedDevice("bed-original", "AA:BB:CC:DD:EE:FF", "old", 1)
            val vm = viewModel()
            runCurrent()
            vm.onAction(PairingAction.Pair)
            runCurrent()

            pairing.events.emit(AssociationEvent.Created(associationId = 2, address = "11:22:33:44:55:66", name = null))
            runCurrent()

            assertThat(store.device?.bedId).isEqualTo("bed-1")
            assertThat(pairing.disassociated).containsExactly(1)
            assertThat(link.disconnects).isEqualTo(1)
        }

    @Test
    fun `cancelling the system dialog ends pairing with a message`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.onAction(PairingAction.Pair)
            runCurrent()

            vm.onAction(PairingAction.SelectionResult(confirmed = false))

            assertThat(vm.uiState.value.inProgress).isFalse()
            assertThat(vm.uiState.value.message).isEqualTo(PairingMessage.Cancelled)
            assertThat(store.device).isNull()
        }

    @Test
    fun `failure is reported`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.onAction(PairingAction.Pair)
            runCurrent()

            pairing.events.emit(AssociationEvent.Failed("timeout"))
            runCurrent()

            assertThat(vm.uiState.value.inProgress).isFalse()
            assertThat(vm.uiState.value.message).isEqualTo(PairingMessage.Failed("timeout"))
        }

    @Test
    fun `name filter is used when enabled and validated`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.onAction(PairingAction.FilterByName(true))
            vm.onAction(PairingAction.NamePatternChanged("[unclosed"))
            vm.onAction(PairingAction.Pair)
            runCurrent()
            assertThat(vm.uiState.value.message).isEqualTo(PairingMessage.InvalidPattern)
            assertThat(pairing.requestedFilters).isEmpty()

            vm.onAction(PairingAction.MessageShown)
            vm.onAction(PairingAction.NamePatternChanged("DPG.*"))
            vm.onAction(PairingAction.Pair)
            runCurrent()
            assertThat(pairing.requestedFilters).containsExactly(PairingFilter.NamePattern("DPG.*"))
        }

    @Test
    fun `unpair removes the association, the saved device and the connection`() =
        runTest(dispatcher) {
            store.device = PairedDevice("bed-1", "AA:BB:CC:DD:EE:FF", null, 4)
            val vm = viewModel()
            runCurrent()

            vm.onAction(PairingAction.Unpair)
            runCurrent()

            assertThat(pairing.disassociated).containsExactly(4)
            assertThat(store.device).isNull()
            assertThat(link.disconnects).isEqualTo(1)
            assertThat(vm.uiState.value.paired).isNull()
            assertThat(vm.uiState.value.message).isEqualTo(PairingMessage.Unpaired)
        }
}
