package dev.linakbed.app.protolab

import dev.linakbed.ble.DiagnosticBedLink
import dev.linakbed.ble.GattServiceInfo
import dev.linakbed.domain.BedResult
import dev.linakbed.domain.port.LinkState
import dev.linakbed.protocol.BedCommand
import dev.linakbed.protocol.HeightReading
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.time.Duration

class FakeDiagnosticBedLink : DiagnosticBedLink {
    override val state = MutableStateFlow<LinkState>(LinkState.Disconnected)
    override val telemetry = MutableSharedFlow<HeightReading>(extraBufferCapacity = 16)
    override var services: List<GattServiceInfo> = emptyList()

    val commands = mutableListOf<BedCommand>()
    val rawWrites = mutableListOf<ByteArray>()
    val connects = mutableListOf<String>()
    var disconnects = 0
    var writeResult: BedResult<Unit> = BedResult.Ok(Unit)

    override suspend fun connect(
        address: String,
        timeout: Duration,
    ): BedResult<Unit> {
        connects += address
        state.value = LinkState.Connected
        return BedResult.Ok(Unit)
    }

    override suspend fun disconnect() {
        disconnects++
        state.value = LinkState.Disconnected
    }

    override suspend fun write(command: BedCommand): BedResult<Unit> {
        commands += command
        return writeResult
    }

    override suspend fun writeRaw(bytes: ByteArray): BedResult<Unit> {
        rawWrites += bytes
        return writeResult
    }
}
