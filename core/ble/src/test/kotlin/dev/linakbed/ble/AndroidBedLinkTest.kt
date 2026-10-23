package dev.linakbed.ble

import android.Manifest
import android.bluetooth.BluetoothStatusCodes
import com.google.common.truth.Truth.assertThat
import dev.linakbed.domain.BedError
import dev.linakbed.domain.BedResult
import dev.linakbed.domain.GattStage
import dev.linakbed.domain.fake.FakeBedLogger
import dev.linakbed.domain.log.LogLevel
import dev.linakbed.domain.port.LinkState
import dev.linakbed.protocol.Actuator
import dev.linakbed.protocol.BedCommand
import dev.linakbed.protocol.Direction
import dev.linakbed.protocol.HeightReading
import dev.linakbed.protocol.LinakGatt
import dev.linakbed.protocol.PresetSlot
import dev.linakbed.protocol.ProtocolProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class AndroidBedLinkTest {
    private val connector = FakeGattConnector()
    private val environment = FakeBleEnvironment()
    private val logger = FakeBedLogger()

    private fun link(
        scope: CoroutineScope,
        profile: ProtocolProfile = ProtocolProfile.DEFAULT,
    ) = AndroidBedLink(connector, environment, profile, logger, scope, BleConfig(sensorPollInterval = 500.milliseconds))

    private suspend fun AndroidBedLink.connectDefault() = connect(ADDRESS, 10.seconds)

    private fun TestScope.collectTelemetry(link: AndroidBedLink): List<HeightReading> {
        val readings = mutableListOf<HeightReading>()
        backgroundScope.launch { link.telemetry.collect { readings += it } }
        runCurrent()
        return readings
    }

    @Test
    fun `connects in order - discover then enable sensor notifications`() =
        runTest {
            val link = link(backgroundScope)

            val result = link.connectDefault()

            assertThat(result).isEqualTo(BedResult.Ok(Unit))
            assertThat(link.state.value).isEqualTo(LinkState.Connected)
            assertThat(connector.last.calls).containsExactly("discover", "notify:head", "notify:feet").inOrder()
            assertThat(link.services).isEqualTo(FakeGattServices.notifySensors())
        }

    @Test
    fun `missing permission fails before any GATT call`() =
        runTest {
            environment.permission = false
            val link = link(backgroundScope)

            val result = link.connectDefault()

            val expected = BedError.PermissionMissing(Manifest.permission.BLUETOOTH_CONNECT)
            assertThat(result).isEqualTo(BedResult.Err(expected))
            assertThat(link.state.value).isEqualTo(LinkState.Failed(expected))
            assertThat(connector.connections).isEmpty()
        }

    @Test
    fun `bluetooth off fails before any GATT call`() =
        runTest {
            environment.enabled = false

            assertThat(link(backgroundScope).connectDefault()).isEqualTo(BedResult.Err(BedError.BluetoothOff))
            assertThat(connector.connections).isEmpty()
        }

    @Test
    fun `unknown device fails with CONNECT stage`() =
        runTest {
            connector.returnsConnection = false

            assertThat(link(backgroundScope).connectDefault())
                .isEqualTo(BedResult.Err(BedError.GattFailure(GattStage.CONNECT, null)))
        }

    @Test
    fun `security exception during connect becomes PermissionMissing`() =
        runTest {
            connector.throwOnConnect = SecurityException("revoked")

            assertThat(link(backgroundScope).connectDefault())
                .isEqualTo(BedResult.Err(BedError.PermissionMissing(Manifest.permission.BLUETOOTH_CONNECT)))
        }

    @Test
    fun `no connect callback within timeout fails with ConnectTimeout and closes`() =
        runTest {
            connector.respondToConnect = false
            val link = link(backgroundScope)

            val result = async { link.connectDefault() }
            advanceTimeBy(10.seconds)
            runCurrent()

            assertThat(result.await()).isEqualTo(BedResult.Err(BedError.ConnectTimeout))
            assertThat(connector.last.closed).isTrue()
        }

    @Test
    fun `connect callback with error status fails and closes`() =
        runTest {
            connector.connectStatus = 133

            val result = link(backgroundScope).connectDefault()

            assertThat(result).isEqualTo(BedResult.Err(BedError.GattFailure(GattStage.CONNECT, 133)))
            assertThat(connector.last.closed).isTrue()
        }

    @Test
    fun `service discovery failure closes the connection`() =
        runTest {
            // discoverStatus는 연결 객체가 생긴 뒤에야 바꿀 수 있으므로, 연결 직후 실패하도록 Fake를 감싼다.
            val failing =
                object : GattConnector by connector {
                    override fun connect(
                        address: String,
                        listener: GattEventListener,
                    ): GattConnection? = connector.connect(address, listener)?.also { (it as FakeGattConnection).discoverStatus = 129 }
                }
            val failingLink = AndroidBedLink(failing, environment, ProtocolProfile.DEFAULT, logger, backgroundScope)

            val result = failingLink.connectDefault()

            assertThat(result).isEqualTo(BedResult.Err(BedError.GattFailure(GattStage.DISCOVER_SERVICES, 129)))
            assertThat(connector.last.closed).isTrue()
            assertThat(failingLink.state.value).isInstanceOf(LinkState.Failed::class.java)
        }

    @Test
    fun `missing control characteristic fails and closes`() =
        runTest {
            connector.services = FakeGattServices.noControl()
            val link = link(backgroundScope)

            val result = link.connectDefault()

            assertThat(result).isEqualTo(BedResult.Err(BedError.GattFailure(GattStage.MISSING_CONTROL_CHARACTERISTIC, null)))
            assertThat(connector.last.closed).isTrue()
            assertThat(link.state.value).isInstanceOf(LinkState.Failed::class.java)
        }

    @Test
    fun `missing sensors still connects`() =
        runTest {
            connector.services = FakeGattServices.noSensors()
            val link = link(backgroundScope)

            assertThat(link.connectDefault()).isEqualTo(BedResult.Ok(Unit))
            assertThat(logger.entries.filter { it.level == LogLevel.WARN }.map { it.message })
                .containsExactly("sensor HEAD not found", "sensor FEET not found")
        }

    @Test
    fun `connecting again to the same address reuses the connection`() =
        runTest {
            val link = link(backgroundScope)
            link.connectDefault()

            assertThat(link.connectDefault()).isEqualTo(BedResult.Ok(Unit))
            assertThat(connector.connections).hasSize(1)
        }

    @Test
    fun `write sends encoder bytes with the profile write type`() =
        runTest {
            val link = link(backgroundScope)
            link.connectDefault()

            val result = link.write(BedCommand.RecallPreset(PresetSlot.P3))

            assertThat(result).isEqualTo(BedResult.Ok(Unit))
            val (bytes, withResponse) = connector.last.writes.single()
            assertThat(bytes).isEqualTo(byteArrayOf(0x0c, 0x00))
            assertThat(withResponse).isTrue()
        }

    @Test
    fun `write without response when the profile says so`() =
        runTest {
            val link = link(backgroundScope, ProtocolProfile.DEFAULT.copy(writeWithResponse = false))
            link.connectDefault()

            link.write(BedCommand.Stop)

            assertThat(
                connector.last.writes
                    .single()
                    .second,
            ).isFalse()
        }

    @Test
    fun `writeRaw passes bytes through unchanged`() =
        runTest {
            val link = link(backgroundScope)
            link.connectDefault()

            link.writeRaw(byteArrayOf(0x12, 0x34))

            assertThat(
                connector.last.writes
                    .single()
                    .first,
            ).isEqualTo(byteArrayOf(0x12, 0x34))
        }

    @Test
    fun `write before connecting fails with Disconnected`() =
        runTest {
            assertThat(link(backgroundScope).write(BedCommand.Stop)).isEqualTo(BedResult.Err(BedError.Disconnected))
        }

    @Test
    fun `write rejected at start maps the return code`() =
        runTest {
            val link = link(backgroundScope)
            link.connectDefault()
            connector.last.writeReturnCode = BluetoothStatusCodes.ERROR_GATT_WRITE_REQUEST_BUSY

            assertThat(link.write(BedCommand.Stop))
                .isEqualTo(BedResult.Err(BedError.WriteFailed(BluetoothStatusCodes.ERROR_GATT_WRITE_REQUEST_BUSY)))
        }

    @Test
    fun `write callback with error status fails`() =
        runTest {
            val link = link(backgroundScope)
            link.connectDefault()
            connector.last.writeStatus = 3

            assertThat(link.write(BedCommand.Stop)).isEqualTo(BedResult.Err(BedError.WriteFailed(3)))
        }

    @Test
    fun `write without callback times out with WRITE stage`() =
        runTest {
            val link = link(backgroundScope)
            link.connectDefault()
            connector.last.respondToWrites = false

            val result = async { link.write(BedCommand.Stop) }
            advanceTimeBy(5.seconds)
            runCurrent()

            assertThat(result.await()).isEqualTo(BedResult.Err(BedError.GattFailure(GattStage.WRITE, null)))
        }

    @Test
    fun `link loss completes the pending write with Disconnected and closes`() =
        runTest {
            val link = link(backgroundScope)
            link.connectDefault()
            val gatt = connector.last
            gatt.respondToWrites = false

            val pending = async { link.write(BedCommand.Move(Actuator.HEAD, Direction.UP)) }
            runCurrent()
            gatt.emit(GattEvent.ConnectionStateChanged(status = 8, connected = false))
            runCurrent()

            assertThat(pending.await()).isEqualTo(BedResult.Err(BedError.Disconnected))
            assertThat(link.state.value).isEqualTo(LinkState.Disconnected)
            assertThat(gatt.closed).isTrue()
            assertThat(link.write(BedCommand.Stop)).isEqualTo(BedResult.Err(BedError.Disconnected))
        }

    @Test
    fun `disconnect closes the connection`() =
        runTest {
            val link = link(backgroundScope)
            link.connectDefault()

            link.disconnect()

            assertThat(connector.last.calls).containsAtLeast("disconnect", "close").inOrder()
            assertThat(link.state.value).isEqualTo(LinkState.Disconnected)
        }

    @Test
    fun `sensor notifications are decoded into telemetry`() =
        runTest {
            val link = link(backgroundScope)
            val readings = collectTelemetry(link)
            link.connectDefault()

            connector.last.emit(GattEvent.CharacteristicChanged(LinakGatt.CHAR_HEAD, byteArrayOf(0xe8.toByte(), 0x03)))
            runCurrent()

            val reading = readings.single()
            assertThat(reading.actuator).isEqualTo(Actuator.HEAD)
            assertThat(reading.raw).isEqualTo(1000)
            assertThat(reading.cm).isWithin(1e-9).of(40.0)
        }

    @Test
    fun `read-only sensors are polled at the configured interval`() =
        runTest {
            connector.services = FakeGattServices.readOnlySensors()
            val link = link(backgroundScope)
            val readings = collectTelemetry(link)
            link.connectDefault()
            val gatt = connector.last
            gatt.readValues[LinakGatt.CHAR_HEAD] = byteArrayOf(0xe8.toByte(), 0x03)
            gatt.readValues[LinakGatt.CHAR_FEET] = byteArrayOf(0xe8.toByte(), 0x03)

            runCurrent()
            assertThat(gatt.calls.filter { it.startsWith("read:") }).containsExactly("read:head", "read:feet").inOrder()
            assertThat(readings.map { it.actuator }).containsExactly(Actuator.HEAD, Actuator.FEET).inOrder()

            advanceTimeBy(500.milliseconds)
            runCurrent()
            assertThat(gatt.calls.count { it.startsWith("read:") }).isEqualTo(4)
        }

    @Test
    fun `logs mask the MAC address and dump transmitted bytes`() =
        runTest {
            val link = link(backgroundScope)
            link.connectDefault()
            link.write(BedCommand.Stop)

            val messages = logger.entries.map { it.message }
            assertThat(messages).contains("connected AA:BB:**:**:**:FF")
            assertThat(messages.none { it.contains(ADDRESS) }).isTrue()
            assertThat(logger.entries.filter { it.level == LogLevel.DEBUG }.map { it.message }).contains("tx ff00 (Stop)")
        }

    private companion object {
        const val ADDRESS = "AA:BB:CC:DD:EE:FF"
    }
}
