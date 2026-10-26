package dev.linakbed.app.simbed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.linakbed.domain.model.PairedDevice
import dev.linakbed.domain.port.LinkState
import dev.linakbed.domain.port.PairingStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 가상 침대 한 대의 화면 상태. */
data class SimBedCard(
    val index: Int,
    val paired: Boolean = false,
    val link: LinkState = LinkState.Disconnected,
    val snapshot: SimulatedBed.Snapshot? = null,
    val failConnect: Boolean = false,
    val received: List<SimulatedBedLink.Received> = emptyList(),
)

data class SimBedUiState(
    val cards: List<SimBedCard> = (1..SimulatedBeds.COUNT).map { SimBedCard(it) },
    /** 침대를 더 연결할 수 있는가 (실제 침대 포함 최대 2대, ADR-012). */
    val canAdd: Boolean = true,
) {
    val anyPaired: Boolean get() = cards.any { it.paired }
}

/** 디버그 전용 가상 침대 화면 (S13: 2대). 침대 제어는 정식 화면으로 하고, 여기서는 연결·상태·고장 흉내만. */
@HiltViewModel
class SimBedViewModel
    @Inject
    constructor(
        private val pairingStore: PairingStore,
        private val sims: SimulatedBeds,
    ) : ViewModel() {
        private fun cardFlow(index: Int): Flow<SimBedCard> {
            val link = sims.link(SimulatedBedLink.addressOf(index))
            return combine(link.state, link.snapshot, link.failConnect, link.received) { state, snapshot, fail, received ->
                SimBedCard(index, link = state, snapshot = snapshot, failConnect = fail, received = received)
            }
        }

        val uiState: StateFlow<SimBedUiState> =
            combine(listOf(pairingStore.observeAll()) + (1..SimulatedBeds.COUNT).map(::cardFlow)) { values ->
                @Suppress("UNCHECKED_CAST")
                val beds = values[0] as List<PairedDevice>
                val addresses = beds.map { it.address }.toSet()
                SimBedUiState(
                    cards =
                        values.drop(1).map {
                            (it as SimBedCard).let { card ->
                                card.copy(
                                    paired =
                                        SimulatedBedLink.addressOf(card.index) in addresses,
                                )
                            }
                        },
                    canAdd = beds.size < PairingStore.MAX_BEDS,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SimBedUiState())

        /** 가상 침대 [index]번을 침대 목록에 추가한다. 저장이 끝난 뒤 [onPaired] — 화면은 이때 정식 앱으로 넘어간다. */
        fun pair(
            index: Int,
            onPaired: () -> Unit = {},
        ) {
            viewModelScope.launch { if (pairingStore.save(SimulatedBeds.device(index))) onPaired() }
        }

        /** 가상 침대 [index]번만 목록에서 뺀다 (실제 침대는 건드리지 않음). 연결은 BedFleet이 끊는다. */
        fun unpair(index: Int) {
            viewModelScope.launch { pairingStore.remove(SimulatedBeds.device(index).bedId) }
        }

        fun setFailConnect(
            index: Int,
            enabled: Boolean,
        ) {
            sims.link(SimulatedBedLink.addressOf(index)).failConnect.value = enabled
        }

        fun dropLink(index: Int) {
            viewModelScope.launch { sims.link(SimulatedBedLink.addressOf(index)).dropLink() }
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
