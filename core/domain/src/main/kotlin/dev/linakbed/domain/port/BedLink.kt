package dev.linakbed.domain.port

import dev.linakbed.domain.BedError
import dev.linakbed.domain.BedResult
import dev.linakbed.domain.preset.PresetPose
import dev.linakbed.protocol.BedCommand
import dev.linakbed.protocol.HeightReading
import dev.linakbed.protocol.PresetSlot
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

    /**
     * 침대에 설정된 프리셋 위치를 읽는다 (ADR-012, S13). 읽을 수 없는 침대는 null — 그때는 도착·저장 때 기록한 값을 쓴다.
     * 실제 Linak 침대는 README 프로토콜에 읽기 명령이 없어 null (HW-S13에서 DPG 메모리 위치 읽기 실험).
     */
    suspend fun readPresetPoses(): Map<PresetSlot, PresetPose>? = null
}

sealed interface LinkState {
    data object Disconnected : LinkState

    data object Connecting : LinkState

    data object Connected : LinkState

    data class Failed(
        val error: BedError,
    ) : LinkState
}
