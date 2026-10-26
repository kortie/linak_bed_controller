package dev.linakbed.app.feature.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.linakbed.app.pairing.AssociationEvent
import dev.linakbed.app.pairing.CompanionPairing
import dev.linakbed.app.pairing.IdGenerator
import dev.linakbed.app.pairing.PairingFilter
import dev.linakbed.domain.model.PairedDevice
import dev.linakbed.domain.port.PairingStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * CompanionDeviceManager 페어링 (ADR-005). 침대를 추가(최대 2대, ADR-012)하거나 한 대씩 해제한다.
 * 같은 침대(MAC)를 다시 페어링하면 기존 bedId를 유지한다 (향후 클라우드 기기 ID, ADR-006).
 * 해제한 침대의 연결은 BedFleet이 목록 변화를 보고 끊는다.
 */
@HiltViewModel
class PairingViewModel
    @Inject
    constructor(
        private val pairing: CompanionPairing,
        private val store: PairingStore,
        private val ids: IdGenerator,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(PairingUiState())
        val uiState: StateFlow<PairingUiState> = mutableState.asStateFlow()

        private var associateJob: Job? = null

        init {
            viewModelScope.launch { store.observeAll().collect { beds -> mutableState.update { it.copy(loaded = true, beds = beds) } } }
        }

        fun onAction(action: PairingAction) {
            when (action) {
                PairingAction.Pair -> pair()
                PairingAction.SelectionLaunched -> mutableState.update { it.copy(pendingSelection = null) }
                is PairingAction.SelectionResult -> if (!action.confirmed) cancelSelection()
                is PairingAction.Unpair -> unpair(action.bedId)
                is PairingAction.FilterByName -> mutableState.update { it.copy(filterByName = action.enabled) }
                is PairingAction.NamePatternChanged -> mutableState.update { it.copy(namePattern = action.pattern) }
                PairingAction.MessageShown -> mutableState.update { it.copy(message = null) }
            }
        }

        private fun pair() {
            val state = uiState.value
            when {
                state.inProgress -> Unit
                !state.canAdd -> mutableState.update { it.copy(message = PairingMessage.Full) }
                else -> filterOf(state)?.let(::associate)
            }
        }

        /** 이름 패턴이 잘못됐으면 안내하고 null. */
        private fun filterOf(state: PairingUiState): PairingFilter? =
            when {
                !state.filterByName -> PairingFilter.ControlServiceUuid
                runCatching { Regex(state.namePattern) }.isSuccess -> PairingFilter.NamePattern(state.namePattern)
                else -> {
                    mutableState.update { it.copy(message = PairingMessage.InvalidPattern) }
                    null
                }
            }

        private fun associate(filter: PairingFilter) {
            mutableState.update { it.copy(inProgress = true) }
            associateJob =
                viewModelScope.launch {
                    pairing.associate(filter).collect { event ->
                        when (event) {
                            is AssociationEvent.Pending -> mutableState.update { it.copy(pendingSelection = event.intentSender) }
                            is AssociationEvent.Created -> onCreated(event)
                            is AssociationEvent.Failed ->
                                mutableState.update { it.copy(inProgress = false, message = PairingMessage.Failed(event.reason)) }
                        }
                    }
                }
        }

        private suspend fun onCreated(event: AssociationEvent.Created) {
            val previous = store.all().firstOrNull { it.address.equals(event.address, ignoreCase = true) }
            val device =
                PairedDevice(
                    bedId = previous?.bedId ?: ids.newId(),
                    address = event.address,
                    name = event.name,
                    cdmAssociationId = event.associationId,
                )
            val message = if (store.save(device)) PairingMessage.Paired(device.name) else PairingMessage.Full
            mutableState.update { it.copy(inProgress = false, message = message) }
        }

        private fun cancelSelection() {
            associateJob?.cancel()
            associateJob = null
            mutableState.update { it.copy(inProgress = false, pendingSelection = null, message = PairingMessage.Cancelled) }
        }

        private fun unpair(bedId: String) {
            val device = uiState.value.beds.firstOrNull { it.bedId == bedId } ?: return
            viewModelScope.launch {
                if (device.cdmAssociationId >= 0) pairing.disassociate(device.cdmAssociationId)
                store.remove(bedId)
                mutableState.update { it.copy(message = PairingMessage.Unpaired) }
            }
        }
    }
