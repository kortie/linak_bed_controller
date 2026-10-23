package dev.linakbed.app.protolab

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import dev.linakbed.protocol.LinakGatt
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** 원시 BLE 스캔 (Protocol Lab 전용 — 정식 페어링은 CompanionDeviceManager, S04). */
fun interface DeviceScanner {
    fun scan(): Flow<ScannedDevice>
}

data class ScannedDevice(
    val address: String,
    val name: String?,
    val rssi: Int,
    /** 광고 패킷에 Linak 제어 서비스 UUID가 들어 있는지 (hw-findings 기록 항목). */
    val advertisesControlService: Boolean,
)

/** 스캔 전에 ProtocolLabScreen이 BLUETOOTH_SCAN 권한을 요청한다. 권한이 없으면 SecurityException → ViewModel이 로그로 남긴다. */
@SuppressLint("MissingPermission")
class AndroidBleScanner(
    private val context: Context,
) : DeviceScanner {
    override fun scan(): Flow<ScannedDevice> =
        callbackFlow {
            val scanner = context.getSystemService(BluetoothManager::class.java)?.adapter?.bluetoothLeScanner
            if (scanner == null) {
                close(IllegalStateException("Bluetooth LE scanner unavailable (Bluetooth off?)"))
                return@callbackFlow
            }
            val callback =
                object : ScanCallback() {
                    override fun onScanResult(
                        callbackType: Int,
                        result: ScanResult,
                    ) {
                        trySend(result.toDevice())
                    }

                    override fun onScanFailed(errorCode: Int) {
                        close(IllegalStateException("scan failed: $errorCode"))
                    }
                }
            val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
            scanner.startScan(null, settings, callback)
            awaitClose { scanner.stopScan(callback) }
        }

    private fun ScanResult.toDevice() =
        ScannedDevice(
            address = device.address,
            name = scanRecord?.deviceName,
            rssi = rssi,
            advertisesControlService = scanRecord?.serviceUuids?.any { it.uuid == LinakGatt.SERVICE_CONTROL } == true,
        )
}
