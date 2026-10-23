package dev.linakbed.app.feature.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.linakbed.app.pairing.AssociationEvent
import dev.linakbed.app.pairing.CompanionPairing
import dev.linakbed.app.pairing.IdGenerator
import dev.linakbed.app.pairing.PairingFilter
import dev.linakbed.domain.model.PairedDevice
import dev.linakbed.domain.port.BedLink
import dev.linakbed.domain.port.PairingStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * CompanionDeviceManager 페어링 (ADR-005). 페어링 정보를 쓰는 유일한 화면이라, 저장할 때 자기 상태도 함께 갱신한다.
 * 같은 침대(MAC)를 다시 페어링하면 기존 bedId를 유지한다 (향후 클라우드 기기 ID, ADR-006).
 */
@HiltViewModel
class PairingViewModel
    @Inject
    constructor(
        private val pairing: CompanionPairing,
        private val store: PairingStore,
        private val link: BedLink,
        private val ids: IdGenerator,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(PairingUiState())
        val uiState: StateFlow<PairingUiState> = mutableState.asStateFlow()

        private var associateJob: Job? = null

        init {
            viewModelScope.launch {
                val device = store.pairedDevice()
                mutableState.update { it.copy(loaded = true, paired = device) }
            }
        }

        fun onAction(action: PairingAction) {
            when (action) {
                PairingAction.Pair -> pair()
                PairingAction.SelectionLaunched -> mutableState.update { it.copy(pendingSelection = null) }
                is PairingAction.SelectionResult -> if (!action.confirmed) cancelSelection()
                PairingAction.Unpair -> unpair()
                is PairingAction.FilterByName -> mutableState.update { it.copy(filterByName = action.enabled) }
                is PairingAction.NamePatternChanged -> mutableState.update { it.copy(namePattern = action.pattern) }
                PairingAction.MessageShown -> mutableState.update { it.copy(message = null) }
            }
        }

        private fun pair() {
            val state = uiState.value
            if (state.inProgress) return
            val filter =
                if (state.filterByName) {
                    if (runCatching { Regex(state.namePattern) }.isFailure) {
                        mutableState.update { it.copy(message = PairingMessage.InvalidPattern) }
                        return
                    }
                    PairingFilter.NamePattern(state.namePattern)
                } else {
                    PairingFilter.ControlServiceUuid
                }
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
            val previous = store.pairedDevice()
            val sameBed = previous != null && previous.address.equals(event.address, ignoreCase = true)
            if (previous != null && !sameBed) {
                // 다른 침대로 바꿈: 이전 CDM 연결과 BLE 연결을 정리한다.
                if (previous.cdmAssociationId >= 0) pairing.disassociate(previous.cdmAssociationId)
                link.disconnect()
            }
            val device =
                PairedDevice(
                    bedId = if (sameBed) previous.bedId else ids.newId(),
                    address = event.address,
                    name = event.name,
                    cdmAssociationId = event.associationId,
                )
            store.save(device)
            mutableState.update { it.copy(inProgress = false, paired = device, message = PairingMessage.Paired(device.name)) }
        }

        private fun cancelSelection() {
            associateJob?.cancel()
            associateJob = null
            mutableState.update { it.copy(inProgress = false, pendingSelection = null, message = PairingMessage.Cancelled) }
        }

        private fun unpair() {
            val device = uiState.value.paired ?: return
            viewModelScope.launch {
                if (device.cdmAssociationId >= 0) pairing.disassociate(device.cdmAssociationId)
                store.save(null)
                link.disconnect()
                mutableState.update { it.copy(paired = null, message = PairingMessage.Unpaired) }
            }
        }
    }
