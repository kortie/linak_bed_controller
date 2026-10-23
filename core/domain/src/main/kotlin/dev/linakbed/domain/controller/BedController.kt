package dev.linakbed.domain.controller

import dev.linakbed.domain.BedError
import dev.linakbed.domain.BedResult
import dev.linakbed.domain.port.LinkState
import dev.linakbed.protocol.Actuator
import dev.linakbed.protocol.Direction
import dev.linakbed.protocol.LightAction
import dev.linakbed.protocol.PresetSlot
import kotlinx.coroutines.flow.StateFlow

/**
 * 수동 UI와 원격 명령이 공유하는 침대 제어의 유일한 진입점. 모든 모터 동작은 중재(아키텍처 §6)와
 * 안전 감독(§7)을 거친다. 연속 이동·프리셋 저장은 source 파라미터가 없어 MANUAL에서만 부를 수 있다 (S4).
 */
interface BedController {
    val status: StateFlow<BedStatus>

    /** 누르고 있는 동안만 움직인다 (S3). 반환된 핸들의 [MotionHandle.release]로 멈춘다. */
    suspend fun startManualMove(
        actuator: Actuator,
        direction: Direction,
    ): BedResult<MotionHandle>

    /** 도착(센서 안정)·안전 정지·중단 중 하나로 끝날 때까지 기다린다. */
    suspend fun goToPreset(
        slot: PresetSlot,
        source: CommandSource,
    ): BedResult<Unit>

    /** 하드웨어 프리셋을 현재 위치로 덮어쓴다. UI는 확인을 받은 뒤에만 부른다 (S6). */
    suspend fun savePreset(slot: PresetSlot): BedResult<Unit>

    /** 조명은 모터와 무관하므로 중재하지 않는다. */
    suspend fun setLight(
        action: LightAction,
        source: CommandSource,
    ): BedResult<Unit>

    /** 항상 수락한다 (S2). 진행 중 동작을 먼저 중단한 뒤 Stop을 보낸다. */
    suspend fun stop(source: CommandSource): BedResult<Unit>

    /** 화면이 떠 있는 동안 연결을 유지한다 (ADR-003). 연결돼 있지 않으면 백그라운드로 연결을 시도한다. */
    fun holdConnection(): ConnectionLease
}

fun interface MotionHandle {
    fun release()
}

fun interface ConnectionLease {
    fun release()
}

data class BedStatus(
    val link: LinkState = LinkState.Disconnected,
    val headCm: Double? = null,
    val feetCm: Double? = null,
    val activeMotion: ActiveMotion? = null,
    /** 침대가 조명 상태를 보고하지 않으므로 마지막 성공 명령 기준 추정. TOGGLE 후·연결이 끊기면 UNKNOWN. */
    val light: LightState = LightState.UNKNOWN,
    val lastError: BedError? = null,
)

data class ActiveMotion(
    val source: CommandSource,
    val kind: MotionKind,
    val preset: PresetSlot? = null,
)

enum class MotionKind { MANUAL_MOVE, PRESET }

enum class LightState { ON, OFF, UNKNOWN }
