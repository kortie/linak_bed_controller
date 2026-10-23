package dev.linakbed.domain.fake

import dev.linakbed.domain.BedError
import dev.linakbed.domain.BedResult
import dev.linakbed.domain.port.BedLink
import dev.linakbed.domain.port.LinkState
import dev.linakbed.protocol.Actuator
import dev.linakbed.protocol.BedCommand
import dev.linakbed.protocol.HeightReading
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.time.Duration

/** 쓰인 명령을 기록하고, 연결 실패·끊김·센서 값을 주입할 수 있는 BedLink. */
class FakeBedLink : BedLink {
    override val state = MutableStateFlow<LinkState>(LinkState.Disconnected)
    override val telemetry = MutableSharedFlow<HeightReading>(extraBufferCapacity = 64)

    val writes = mutableListOf<BedCommand>()
    val connects = mutableListOf<String>()
    var disconnects = 0
        private set

    /** 차례대로 쓰이는 connect 결과. 비면 성공. */
    val connectResults = ArrayDeque<BedResult<Unit>>()

    /** 명령별 쓰기 결과. 기본은 성공. */
    var writeResult: (BedCommand) -> BedResult<Unit> = { BedResult.Ok(Unit) }

    override suspend fun connect(
        address: String,
        timeout: Duration,
    ): BedResult<Unit> {
        connects += address
        val result = connectResults.removeFirstOrNull() ?: BedResult.Ok(Unit)
        if (result is BedResult.Ok) state.value = LinkState.Connected
        return result
    }

    override suspend fun disconnect() {
        disconnects++
        state.value = LinkState.Disconnected
    }

    override suspend fun write(command: BedCommand): BedResult<Unit> {
        if (state.value != LinkState.Connected) return BedResult.Err(BedError.Disconnected)
        writes += command
        return writeResult(command)
    }

    /** 연결이 예고 없이 끊김 (S5 시나리오). */
    fun dropLink() {
        state.value = LinkState.Disconnected
    }

    fun emitHeight(
        actuator: Actuator,
        raw: Int,
    ) {
        check(telemetry.tryEmit(HeightReading(actuator, raw, raw / CM_DIVISOR, null)))
    }

    private companion object {
        const val CM_DIVISOR = 25.0
    }
}
