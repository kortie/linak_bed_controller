package dev.linakbed.app.protolab

import com.google.common.truth.Truth.assertThat
import dev.linakbed.ble.CharacteristicProperty
import dev.linakbed.ble.GattCharacteristicInfo
import dev.linakbed.ble.GattServiceInfo
import dev.linakbed.domain.port.LinkState
import dev.linakbed.protocol.Actuator
import dev.linakbed.protocol.HeightReading
import dev.linakbed.protocol.LinakGatt
import org.junit.Test

class ProtocolLabReportTest {
    private val state =
        ProtocolLabUiState(
            devices = listOf(ScannedDevice("AA:BB:CC:DD:EE:FF", "DPG1M", -60, advertisesControlService = true)),
            selectedAddress = "AA:BB:CC:DD:EE:FF",
            link = LinkState.Connected,
            services =
                listOf(
                    GattServiceInfo(
                        LinakGatt.SERVICE_CONTROL,
                        listOf(GattCharacteristicInfo(LinakGatt.CHAR_CONTROL, setOf(CharacteristicProperty.WRITE))),
                    ),
                ),
            head = HeightReading(Actuator.HEAD, 1000, 40.0, null),
            log = listOf("07:00:00.000 0e00 RecallPreset(slot=P1) → OK"),
        )

    @Test
    fun `report contains what hw-findings needs`() {
        val report = ProtocolLabReport.render(state)

        assertThat(report).contains("device: AA:BB:**:**:**:FF")
        assertThat(report).contains("advertised name: DPG1M, control service UUID in advertisement: true")
        assertThat(report).contains("link: Connected")
        assertThat(report).contains("    ${LinakGatt.CHAR_CONTROL} [WRITE]")
        assertThat(report).contains("head: raw=1000 cm=40.00 speed=-")
        assertThat(report).contains("feet: -")
        assertThat(report).contains("  07:00:00.000 0e00 RecallPreset(slot=P1) → OK")
    }

    @Test
    fun `report never contains the full MAC address`() {
        assertThat(ProtocolLabReport.render(state)).doesNotContain("AA:BB:CC:DD:EE:FF")
    }
}
