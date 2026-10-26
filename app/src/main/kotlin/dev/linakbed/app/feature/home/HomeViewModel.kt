package dev.linakbed.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.linakbed.domain.controller.BedActions
import dev.linakbed.domain.controller.BedFleet
import dev.linakbed.domain.controller.CommandSource
import dev.linakbed.domain.port.PairingStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    /** 카드 순서대로의 침대 bedId (ADR-012). */
    val bedIds: List<String> = emptyList(),
) {
    /** 침대를 더 추가할 수 있으면 마지막에 "침대 추가" 카드. */
    val canAdd: Boolean get() = bedIds.size < PairingStore.MAX_BEDS
}

/** 홈 (S13): 침대 카드 목록과 전체 STOP. 카드 안의 조작은 침대별 ViewModel이 맡는다. */
@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        fleet: BedFleet,
        private val all: BedActions,
    ) : ViewModel() {
        val uiState: StateFlow<HomeUiState> =
            fleet.beds
                .map { beds -> HomeUiState(beds.map { it.device.bedId }) }
                .stateIn(viewModelScope, SharingStarted.Eagerly, HomeUiState(fleet.beds.value.map { it.device.bedId }))

        /** 모든 침대 정지 (S2, ADR-012). */
        fun stopAll() {
            viewModelScope.launch { all.stop(CommandSource.MANUAL) }
        }
    }
