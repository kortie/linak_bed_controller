package dev.linakbed.ble

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager

class AndroidBleEnvironment(
    private val context: Context,
) : BleEnvironment {
    override fun hasConnectPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    override fun isBluetoothEnabled(): Boolean = context.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true
}
