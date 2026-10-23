package dev.linakbed.app.feature.pairing

import android.content.IntentSender
import dev.linakbed.app.pairing.PairingFilter
import dev.linakbed.domain.model.PairedDevice

data class PairingUiState(
    val loaded: Boolean = false,
    val paired: PairedDevice? = null,
    /** false = 제어 서비스 UUID로 찾기(권장), true = 이름 패턴으로 찾기 (광고에 UUID가 없는 침대용). */
    val filterByName: Boolean = false,
    val namePattern: String = PairingFilter.MATCH_ALL,
    val inProgress: Boolean = false,
    /** 화면이 시스템 기기 선택 창을 띄워야 할 때. 띄운 뒤 [PairingAction.SelectionLaunched]로 지운다. */
    val pendingSelection: IntentSender? = null,
    val message: PairingMessage? = null,
)

sealed interface PairingMessage {
    data class Paired(
        val name: String?,
    ) : PairingMessage

    data object Cancelled : PairingMessage

    data class Failed(
        val reason: String?,
    ) : PairingMessage

    data object InvalidPattern : PairingMessage

    data object Unpaired : PairingMessage
}

sealed interface PairingAction {
    data object Pair : PairingAction

    data object SelectionLaunched : PairingAction

    /** 시스템 선택 창의 결과. 취소면 false. 성공이면 결과는 연결 콜백으로 온다. */
    data class SelectionResult(
        val confirmed: Boolean,
    ) : PairingAction

    data object Unpair : PairingAction

    data class FilterByName(
        val enabled: Boolean,
    ) : PairingAction

    data class NamePatternChanged(
        val pattern: String,
    ) : PairingAction

    data object MessageShown : PairingAction
}
