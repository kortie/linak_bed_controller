package dev.linakbed.app.simbed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.linakbed.domain.model.PairedDevice
import dev.linakbed.domain.port.LinkState
import dev.linakbed.domain.port.PairingStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SimPairing { NONE, SIMULATED, REAL }

data class SimBedUiState(
    val pairing: SimPairing = SimPairing.NONE,
    val link: LinkState = LinkState.Disconnected,
    val snapshot: SimulatedBed.Snapshot? = null,
    val failConnect: Boolean = false,
    val received: List<SimulatedBedLink.Received> = emptyList(),
)

/** 디버그 전용 가상 침대 화면. 침대 제어는 정식 화면(BedController)으로 하고, 여기서는 연결·상태·고장 흉내만. */
@HiltViewModel
class SimBedViewModel
    @Inject
    constructor(
        private val pairingStore: PairingStore,
        private val link: SimulatedBedLink,
    ) : ViewModel() {
        private val pairing = MutableStateFlow(SimPairing.NONE)

        val uiState: StateFlow<SimBedUiState> =
            combine(pairing, link.state, link.snapshot, link.failConnect, link.received) { pairing, state, snapshot, fail, received ->
                SimBedUiState(pairing, state, snapshot, fail, received)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SimBedUiState())

        init {
            refresh()
        }

        fun refresh() {
            viewModelScope.launch { pairing.value = classify(pairingStore.pairedDevice()) }
        }

        /** 기존 페어링(실제 침대 포함)을 가상 침대로 바꾼다. 실제 침대의 CDM 연결은 그대로 남는다. */
        fun pairSimulated() {
            viewModelScope.launch {
                pairingStore.save(DEVICE)
                refresh()
            }
        }

        fun unpairSimulated() {
            viewModelScope.launch {
                if (classify(pairingStore.pairedDevice()) == SimPairing.SIMULATED) {
                    link.disconnect()
                    pairingStore.save(null)
                }
                refresh()
            }
        }

        fun setFailConnect(enabled: Boolean) {
            link.failConnect.value = enabled
        }

        fun dropLink() {
            viewModelScope.launch { link.dropLink() }
        }

        private fun classify(device: PairedDevice?): SimPairing =
            when {
                device == null -> SimPairing.NONE
                device.address == SimulatedBedLink.ADDRESS -> SimPairing.SIMULATED
                else -> SimPairing.REAL
            }

        companion object {
            val DEVICE = PairedDevice(bedId = "simulated-bed", address = SimulatedBedLink.ADDRESS, name = "가상 침대", cdmAssociationId = -1)
            private const val STOP_TIMEOUT_MS = 5_000L
        }
    }
