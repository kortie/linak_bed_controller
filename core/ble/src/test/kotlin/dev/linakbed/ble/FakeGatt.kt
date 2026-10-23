package dev.linakbed.ble

import android.bluetooth.BluetoothStatusCodes
import dev.linakbed.protocol.LinakGatt
import java.util.UUID

/** 테스트용 GATT. 기본은 모든 호출에 성공 이벤트로 즉시 응답한다. */
class FakeGattConnector : GattConnector {
    var returnsConnection = true
    var respondToConnect = true
    var connectStatus = 0
    var throwOnConnect: RuntimeException? = null
    var services: List<GattServiceInfo> = FakeGattServices.notifySensors()
    val connections = mutableListOf<FakeGattConnection>()
    val last: FakeGattConnection get() = connections.last()

    override fun connect(
        address: String,
        listener: GattEventListener,
    ): GattConnection? {
        throwOnConnect?.let { throw it }
        if (!returnsConnection) return null
        val connection = FakeGattConnection(listener, services)
        connections += connection
        if (respondToConnect) listener.onEvent(GattEvent.ConnectionStateChanged(connectStatus, connectStatus == 0))
        return connection
    }
}

class FakeGattConnection(
    private val listener: GattEventListener,
    private val serviceList: List<GattServiceInfo>,
) : GattConnection {
    val calls = mutableListOf<String>()
    val writes = mutableListOf<Pair<ByteArray, Boolean>>()
    var respondToWrites = true
    var discoverStatus = 0
    var writeReturnCode = BluetoothStatusCodes.SUCCESS
    var writeStatus = 0
    var notifyStatus = 0
    val readValues = mutableMapOf<UUID, ByteArray>()
    var closed = false
        private set

    fun emit(event: GattEvent) = listener.onEvent(event)

    override fun discoverServices(): Boolean {
        calls += "discover"
        emit(GattEvent.ServicesDiscovered(discoverStatus))
        return true
    }

    override fun services(): List<GattServiceInfo> = serviceList

    override fun writeCharacteristic(
        service: UUID,
        characteristic: UUID,
        value: ByteArray,
        withResponse: Boolean,
    ): Int {
        calls += "write"
        writes += value to withResponse
        if (writeReturnCode != BluetoothStatusCodes.SUCCESS) return writeReturnCode
        if (respondToWrites) emit(GattEvent.CharacteristicWritten(characteristic, writeStatus))
        return BluetoothStatusCodes.SUCCESS
    }

    override fun enableNotifications(
        service: UUID,
        characteristic: UUID,
    ): Boolean {
        calls += "notify:${name(characteristic)}"
        emit(GattEvent.DescriptorWritten(characteristic, notifyStatus))
        return true
    }

    override fun readCharacteristic(
        service: UUID,
        characteristic: UUID,
    ): Boolean {
        calls += "read:${name(characteristic)}"
        emit(GattEvent.CharacteristicRead(characteristic, readValues[characteristic] ?: ByteArray(0), 0))
        return true
    }

    override fun disconnect() {
        calls += "disconnect"
    }

    override fun close() {
        calls += "close"
        closed = true
    }

    private fun name(uuid: UUID) =
        when (uuid) {
            LinakGatt.CHAR_HEAD -> "head"
            LinakGatt.CHAR_FEET -> "feet"
            else -> uuid.toString()
        }
}

object FakeGattServices {
    private val control =
        GattServiceInfo(
            LinakGatt.SERVICE_CONTROL,
            listOf(GattCharacteristicInfo(LinakGatt.CHAR_CONTROL, setOf(CharacteristicProperty.WRITE))),
        )

    private fun sensors(vararg properties: CharacteristicProperty) =
        GattServiceInfo(
            LinakGatt.SERVICE_SENSOR,
            listOf(
                GattCharacteristicInfo(LinakGatt.CHAR_HEAD, properties.toSet()),
                GattCharacteristicInfo(LinakGatt.CHAR_FEET, properties.toSet()),
            ),
        )

    fun notifySensors() = listOf(control, sensors(CharacteristicProperty.NOTIFY, CharacteristicProperty.READ))

    fun readOnlySensors() = listOf(control, sensors(CharacteristicProperty.READ))

    fun noSensors() = listOf(control)

    fun noControl() = listOf(sensors(CharacteristicProperty.NOTIFY))
}

class FakeBleEnvironment(
    var permission: Boolean = true,
    var enabled: Boolean = true,
) : BleEnvironment {
    override fun hasConnectPermission() = permission

    override fun isBluetoothEnabled() = enabled
}
