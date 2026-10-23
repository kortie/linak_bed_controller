package dev.linakbed.ble

import java.util.UUID

/**
 * `BluetoothGatt` 위의 얇은 래퍼. 테스트에서는 Fake로 바꿔 끼운다.
 * 호출은 "시작"만 하고, 결과는 [GattEventListener]로 비동기 통지된다 (Android GATT 콜백 그대로).
 */
interface GattConnector {
    /** 연결을 시작한다. 기기를 찾을 수 없으면 null. 결과는 [GattEvent.ConnectionStateChanged]로 온다. */
    fun connect(
        address: String,
        listener: GattEventListener,
    ): GattConnection?
}

interface GattConnection {
    fun discoverServices(): Boolean

    fun services(): List<GattServiceInfo>

    /** @return `BluetoothStatusCodes` 값 (`SUCCESS`면 시작됨, 결과는 [GattEvent.CharacteristicWritten]). */
    fun writeCharacteristic(
        service: UUID,
        characteristic: UUID,
        value: ByteArray,
        withResponse: Boolean,
    ): Int

    /** notify를 켠다 (CCCD 쓰기). 결과는 [GattEvent.DescriptorWritten]. */
    fun enableNotifications(
        service: UUID,
        characteristic: UUID,
    ): Boolean

    /** 결과는 [GattEvent.CharacteristicRead]. */
    fun readCharacteristic(
        service: UUID,
        characteristic: UUID,
    ): Boolean

    fun disconnect()

    fun close()
}

fun interface GattEventListener {
    fun onEvent(event: GattEvent)
}

/** 연결 전 환경 확인 (권한·블루투스 켜짐). */
interface BleEnvironment {
    fun hasConnectPermission(): Boolean

    fun isBluetoothEnabled(): Boolean
}

sealed interface GattEvent {
    data class ConnectionStateChanged(
        val status: Int,
        val connected: Boolean,
    ) : GattEvent

    data class ServicesDiscovered(
        val status: Int,
    ) : GattEvent

    data class CharacteristicWritten(
        val characteristic: UUID,
        val status: Int,
    ) : GattEvent

    data class DescriptorWritten(
        val characteristic: UUID,
        val status: Int,
    ) : GattEvent

    class CharacteristicRead(
        val characteristic: UUID,
        val value: ByteArray,
        val status: Int,
    ) : GattEvent

    class CharacteristicChanged(
        val characteristic: UUID,
        val value: ByteArray,
    ) : GattEvent
}

data class GattServiceInfo(
    val uuid: UUID,
    val characteristics: List<GattCharacteristicInfo>,
)

data class GattCharacteristicInfo(
    val uuid: UUID,
    val properties: Set<CharacteristicProperty>,
)

enum class CharacteristicProperty { READ, WRITE, WRITE_NO_RESPONSE, NOTIFY, INDICATE }

fun List<GattServiceInfo>.characteristic(
    service: UUID,
    characteristic: UUID,
): GattCharacteristicInfo? = firstOrNull { it.uuid == service }?.characteristics?.firstOrNull { it.uuid == characteristic }
