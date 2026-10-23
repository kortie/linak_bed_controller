package dev.linakbed.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import dev.linakbed.protocol.LinakGatt
import java.util.UUID

/**
 * 실제 Android GATT 연결. 로직 없이 호출·콜백을 그대로 옮기기만 한다 (로직은 [AndroidBedLink]에서 Fake로 테스트).
 *
 * BLUETOOTH_CONNECT 권한은 [AndroidBedLink]가 [BleEnvironment]로 매 연결 전에 확인하고,
 * 도중에 회수되어 SecurityException이 나면 그쪽에서 PermissionMissing으로 바꾼다 → 여기서는 lint 검사를 끈다.
 */
@SuppressLint("MissingPermission")
class AndroidGattConnector(
    private val context: Context,
) : GattConnector {
    override fun connect(
        address: String,
        listener: GattEventListener,
    ): GattConnection? {
        val device =
            context
                .getSystemService(BluetoothManager::class.java)
                ?.adapter
                ?.takeIf { BluetoothAdapter.checkBluetoothAddress(address) }
                ?.getRemoteDevice(address)
        return device?.connectGatt(context, false, Callback(listener), BluetoothDevice.TRANSPORT_LE)?.let(::AndroidGattConnection)
    }

    private class Callback(
        private val listener: GattEventListener,
    ) : BluetoothGattCallback() {
        override fun onConnectionStateChange(
            gatt: BluetoothGatt,
            status: Int,
            newState: Int,
        ) = listener.onEvent(GattEvent.ConnectionStateChanged(status, newState == BluetoothProfile.STATE_CONNECTED))

        override fun onServicesDiscovered(
            gatt: BluetoothGatt,
            status: Int,
        ) = listener.onEvent(GattEvent.ServicesDiscovered(status))

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) = listener.onEvent(GattEvent.CharacteristicWritten(characteristic.uuid, status))

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
        ) = listener.onEvent(GattEvent.DescriptorWritten(descriptor.characteristic.uuid, status))

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int,
        ) = listener.onEvent(GattEvent.CharacteristicRead(characteristic.uuid, value, status))

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) = listener.onEvent(GattEvent.CharacteristicChanged(characteristic.uuid, value))
    }
}

@SuppressLint("MissingPermission")
private class AndroidGattConnection(
    private val gatt: BluetoothGatt,
) : GattConnection {
    override fun discoverServices(): Boolean = gatt.discoverServices()

    override fun services(): List<GattServiceInfo> =
        gatt.services.map { service ->
            GattServiceInfo(service.uuid, service.characteristics.map { GattCharacteristicInfo(it.uuid, it.properties.toSet()) })
        }

    override fun writeCharacteristic(
        service: UUID,
        characteristic: UUID,
        value: ByteArray,
        withResponse: Boolean,
    ): Int {
        val target = find(service, characteristic) ?: return BluetoothStatusCodes.ERROR_UNKNOWN
        val writeType =
            if (withResponse) BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT else BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        return gatt.writeCharacteristic(target, value, writeType)
    }

    override fun enableNotifications(
        service: UUID,
        characteristic: UUID,
    ): Boolean {
        val target = find(service, characteristic)
        val cccd = target?.getDescriptor(LinakGatt.CLIENT_CHARACTERISTIC_CONFIG)
        return target != null &&
            cccd != null &&
            gatt.setCharacteristicNotification(target, true) &&
            gatt.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) == BluetoothStatusCodes.SUCCESS
    }

    override fun readCharacteristic(
        service: UUID,
        characteristic: UUID,
    ): Boolean = find(service, characteristic)?.let(gatt::readCharacteristic) ?: false

    override fun disconnect() = gatt.disconnect()

    override fun close() = gatt.close()

    private fun find(
        service: UUID,
        characteristic: UUID,
    ): BluetoothGattCharacteristic? = gatt.getService(service)?.getCharacteristic(characteristic)

    private fun Int.toSet(): Set<CharacteristicProperty> =
        buildSet {
            if (this@toSet and BluetoothGattCharacteristic.PROPERTY_READ != 0) add(CharacteristicProperty.READ)
            if (this@toSet and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) add(CharacteristicProperty.WRITE)
            if (this@toSet and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) add(CharacteristicProperty.WRITE_NO_RESPONSE)
            if (this@toSet and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) add(CharacteristicProperty.NOTIFY)
            if (this@toSet and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) add(CharacteristicProperty.INDICATE)
        }
}
