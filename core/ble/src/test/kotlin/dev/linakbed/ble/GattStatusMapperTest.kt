package dev.linakbed.ble

import android.Manifest
import android.bluetooth.BluetoothStatusCodes
import com.google.common.truth.Truth.assertThat
import dev.linakbed.domain.BedError
import dev.linakbed.domain.GattStage
import org.junit.Test

class GattStatusMapperTest {
    @Test
    fun `write return codes`() {
        assertThat(GattStatusMapper.fromWriteReturnCode(BluetoothStatusCodes.SUCCESS)).isNull()
        assertThat(GattStatusMapper.fromWriteReturnCode(BluetoothStatusCodes.ERROR_MISSING_BLUETOOTH_CONNECT_PERMISSION))
            .isEqualTo(BedError.PermissionMissing(Manifest.permission.BLUETOOTH_CONNECT))
        assertThat(GattStatusMapper.fromWriteReturnCode(BluetoothStatusCodes.ERROR_BLUETOOTH_NOT_ENABLED)).isEqualTo(BedError.BluetoothOff)
        assertThat(GattStatusMapper.fromWriteReturnCode(BluetoothStatusCodes.ERROR_GATT_WRITE_REQUEST_BUSY))
            .isEqualTo(BedError.WriteFailed(BluetoothStatusCodes.ERROR_GATT_WRITE_REQUEST_BUSY))
    }

    @Test
    fun `write callback status`() {
        assertThat(GattStatusMapper.fromWriteStatus(0)).isNull()
        assertThat(GattStatusMapper.fromWriteStatus(GATT_ERROR)).isEqualTo(BedError.WriteFailed(GATT_ERROR))
    }

    @Test
    fun `connect failure status`() {
        assertThat(GattStatusMapper.fromConnectFailure(GattStatusMapper.GATT_CONN_TIMEOUT)).isEqualTo(BedError.ConnectTimeout)
        assertThat(GattStatusMapper.fromConnectFailure(GATT_ERROR)).isEqualTo(BedError.GattFailure(GattStage.CONNECT, GATT_ERROR))
        assertThat(GattStatusMapper.fromConnectFailure(0)).isEqualTo(BedError.GattFailure(GattStage.CONNECT, 0))
    }

    @Test
    fun `stage status`() {
        assertThat(GattStatusMapper.fromStageStatus(GattStage.READ, 0)).isNull()
        assertThat(GattStatusMapper.fromStageStatus(GattStage.DISCOVER_SERVICES, GATT_ERROR))
            .isEqualTo(BedError.GattFailure(GattStage.DISCOVER_SERVICES, GATT_ERROR))
    }

    private companion object {
        /** Android의 흔한 연결 오류 status (GATT_ERROR). */
        const val GATT_ERROR = 133
    }
}
