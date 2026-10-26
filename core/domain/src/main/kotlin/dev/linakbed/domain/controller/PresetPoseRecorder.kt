package dev.linakbed.domain.controller

import dev.linakbed.domain.BedResult
import dev.linakbed.domain.port.PresetPoseStore
import dev.linakbed.domain.preset.PresetPose
import dev.linakbed.protocol.PresetSlot

/**
 * [BedController] 장식자 (S12): 프리셋에 도착했거나 프리셋 저장이 성공하면 그때의 머리·발 높이를 [bedId] 침대의 자세로 기록한다.
 * 수동·원격 모든 경로가 BedController를 거치므로 여기 한 곳이면 된다. 나머지 동작은 그대로 위임한다.
 */
class PresetPoseRecorder(
    private val delegate: BedController,
    private val store: PresetPoseStore,
    private val bedId: String,
) : BedController by delegate {
    override suspend fun goToPreset(
        slot: PresetSlot,
        source: CommandSource,
    ): BedResult<Unit> = delegate.goToPreset(slot, source).also { if (it is BedResult.Ok) recordCurrent(slot) }

    override suspend fun savePreset(slot: PresetSlot): BedResult<Unit> =
        delegate.savePreset(slot).also { if (it is BedResult.Ok) recordCurrent(slot) }

    private suspend fun recordCurrent(slot: PresetSlot) {
        val status = delegate.status.value
        val head = status.headCm ?: return
        val feet = status.feetCm ?: return
        store.record(bedId, slot, PresetPose(head, feet))
    }
}
