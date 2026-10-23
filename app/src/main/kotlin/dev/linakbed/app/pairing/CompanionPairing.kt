package dev.linakbed.app.pairing

import android.content.IntentSender
import kotlinx.coroutines.flow.Flow

/**
 * CompanionDeviceManager 래퍼 (ADR-005). 테스트에서는 Fake로 바꿔 끼운다.
 * [associate]는 시스템 기기 선택 창을 띄울 [AssociationEvent.Pending] → 결과([AssociationEvent.Created]/[AssociationEvent.Failed]) 순으로 낸다.
 */
interface CompanionPairing {
    fun associate(filter: PairingFilter): Flow<AssociationEvent>

    fun disassociate(associationId: Int)
}

sealed interface AssociationEvent {
    /** 화면이 [intentSender]로 시스템 선택 창을 띄워야 한다. */
    class Pending(
        val intentSender: IntentSender,
    ) : AssociationEvent

    data class Created(
        val associationId: Int,
        /** 대문자 MAC (`BluetoothAdapter.checkBluetoothAddress` 형식). */
        val address: String,
        val name: String?,
    ) : AssociationEvent

    data class Failed(
        val reason: String?,
    ) : AssociationEvent
}

/** 시스템 선택 창에 어떤 기기를 보여 줄지. 광고에 서비스 UUID가 없는 침대를 위해 이름 패턴으로 바꿀 수 있다 (HW-S04). */
sealed interface PairingFilter {
    data object ControlServiceUuid : PairingFilter

    data class NamePattern(
        val regex: String,
    ) : PairingFilter

    companion object {
        const val MATCH_ALL = ".*"
    }
}
