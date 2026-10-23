package dev.linakbed.domain.port

import dev.linakbed.domain.BedError
import dev.linakbed.domain.BedResult
import dev.linakbed.protocol.BedCommand
import dev.linakbed.protocol.HeightReading
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Duration

/**
 * 침대와의 연결 (구현: core:ble의 AndroidBedLink). 모터 동작은 반드시 BedController를 거쳐 이 포트를 쓴다.
 * BLE 개념(GATT, characteristic)을 드러내지 않아, 향후 브리지 경유 구현으로 바꿔 끼울 수 있다 (ADR-006).
 */
interface BedLink {
    val state: StateFlow<LinkState>
    val telemetry: Flow<HeightReading>

    /** [address]의 해석은 구현 몫 (BLE면 MAC 주소). */
    suspend fun connect(
        address: String,
        timeout: Duration,
    ): BedResult<Unit>

    suspend fun disconnect()

    /** 바이트 인코딩은 구현이 LinakCommandEncoder로 한다. */
    suspend fun write(command: BedCommand): BedResult<Unit>
}

sealed interface LinkState {
    data object Disconnected : LinkState

    data object Connecting : LinkState

    data object Connected : LinkState

    data class Failed(
        val error: BedError,
    ) : LinkState
}
