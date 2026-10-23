package dev.linakbed.ble

import android.Manifest
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothStatusCodes
import dev.linakbed.domain.BedError
import dev.linakbed.domain.GattStage

/** Android GATT 상태값 → [BedError]의 단일 매핑 지점 (linak-conventions §3). */
object GattStatusMapper {
    /** 연결 감시 시간 초과 (`GATT_CONN_TIMEOUT`, Android 내부 상수로 공개 API에 없음). */
    const val GATT_CONN_TIMEOUT = 8

    /** `BluetoothGatt.writeCharacteristic`의 반환값. 시작됐으면 null. */
    fun fromWriteReturnCode(code: Int): BedError? =
        when (code) {
            BluetoothStatusCodes.SUCCESS -> null
            BluetoothStatusCodes.ERROR_MISSING_BLUETOOTH_CONNECT_PERMISSION ->
                BedError.PermissionMissing(Manifest.permission.BLUETOOTH_CONNECT)
            BluetoothStatusCodes.ERROR_BLUETOOTH_NOT_ENABLED -> BedError.BluetoothOff
            else -> BedError.WriteFailed(code)
        }

    /** `onCharacteristicWrite`의 status. 성공이면 null. */
    fun fromWriteStatus(status: Int): BedError? = if (status == BluetoothGatt.GATT_SUCCESS) null else BedError.WriteFailed(status)

    /** 연결 시도 결과가 "연결됨/성공"이 아닐 때. */
    fun fromConnectFailure(status: Int): BedError =
        if (status == GATT_CONN_TIMEOUT) BedError.ConnectTimeout else BedError.GattFailure(GattStage.CONNECT, status)

    /** 연결 이후 단계(탐색·notify·읽기)의 status. 성공이면 null. */
    fun fromStageStatus(
        stage: GattStage,
        status: Int,
    ): BedError? = if (status == BluetoothGatt.GATT_SUCCESS) null else BedError.GattFailure(stage, status)
}
