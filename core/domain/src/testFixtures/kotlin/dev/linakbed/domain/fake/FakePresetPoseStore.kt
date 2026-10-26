package dev.linakbed.domain.fake

import dev.linakbed.domain.port.PresetPoseStore
import dev.linakbed.domain.preset.PresetPose
import dev.linakbed.protocol.PresetSlot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakePresetPoseStore : PresetPoseStore {
    /** bedId → 슬롯별 자세. */
    val current = MutableStateFlow<Map<String, Map<PresetSlot, PresetPose>>>(emptyMap())

    fun of(bedId: String): Map<PresetSlot, PresetPose> = current.value[bedId].orEmpty()

    override fun poses(bedId: String): Flow<Map<PresetSlot, PresetPose>> = current.map { it[bedId].orEmpty() }

    override suspend fun record(
        bedId: String,
        slot: PresetSlot,
        pose: PresetPose,
    ) {
        current.value = current.value + (bedId to (of(bedId) + (slot to pose)))
    }
}
