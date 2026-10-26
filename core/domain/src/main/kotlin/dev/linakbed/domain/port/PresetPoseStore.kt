package dev.linakbed.domain.port

import dev.linakbed.domain.preset.PresetPose
import dev.linakbed.protocol.PresetSlot
import kotlinx.coroutines.flow.Flow

/**
 * 침대별·프리셋별로 알고 있는 침대 자세 (S12·S13). 침대가 읽어 준 설정값이 있으면 그것, 없으면 도착·저장 때 기록한 값.
 * 아직 모르는 슬롯은 맵에 없다.
 */
interface PresetPoseStore {
    fun poses(bedId: String): Flow<Map<PresetSlot, PresetPose>>

    suspend fun record(
        bedId: String,
        slot: PresetSlot,
        pose: PresetPose,
    )
}
