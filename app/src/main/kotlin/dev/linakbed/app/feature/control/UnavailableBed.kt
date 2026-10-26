package dev.linakbed.app.feature.control

import dev.linakbed.domain.BedError
import dev.linakbed.domain.BedResult
import dev.linakbed.domain.controller.BedController
import dev.linakbed.domain.controller.BedStatus
import dev.linakbed.domain.controller.CommandSource
import dev.linakbed.domain.controller.ConnectionLease
import dev.linakbed.domain.controller.MotionHandle
import dev.linakbed.protocol.Actuator
import dev.linakbed.protocol.Direction
import dev.linakbed.protocol.LightAction
import dev.linakbed.protocol.PresetSlot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 카드가 가리키는 침대가 방금 해제돼 컨트롤러가 없을 때: 모든 동작이 [BedError.NotPaired]. */
object UnavailableBed : BedController {
    private val notPaired = BedResult.Err(BedError.NotPaired)
    override val status: StateFlow<BedStatus> = MutableStateFlow(BedStatus())

    override suspend fun startManualMove(
        actuator: Actuator,
        direction: Direction,
    ): BedResult<MotionHandle> = notPaired

    override suspend fun goToPreset(
        slot: PresetSlot,
        source: CommandSource,
    ): BedResult<Unit> = notPaired

    override suspend fun savePreset(slot: PresetSlot): BedResult<Unit> = notPaired

    override suspend fun setLight(
        action: LightAction,
        source: CommandSource,
    ): BedResult<Unit> = notPaired

    override suspend fun stop(source: CommandSource): BedResult<Unit> = notPaired

    override fun holdConnection(): ConnectionLease = ConnectionLease {}
}
