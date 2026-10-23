package dev.linakbed.domain.fake

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

/** 호출을 기록하는 BedController (UI·원격 핸들러 테스트용). */
class FakeBedController : BedController {
    override val status = MutableStateFlow(BedStatus())
    val calls = mutableListOf<String>()
    var result: BedResult<Unit> = BedResult.Ok(Unit)
    var releases = 0
        private set
    var activeLeases = 0
    var leaseRequests = 0
        private set

    override suspend fun startManualMove(
        actuator: Actuator,
        direction: Direction,
    ): BedResult<MotionHandle> {
        calls += "move $actuator $direction"
        return when (val r = result) {
            is BedResult.Ok -> BedResult.Ok(MotionHandle { releases++ })
            is BedResult.Err -> r
        }
    }

    override suspend fun goToPreset(
        slot: PresetSlot,
        source: CommandSource,
    ): BedResult<Unit> {
        calls += "preset $slot $source"
        return result
    }

    override suspend fun savePreset(slot: PresetSlot): BedResult<Unit> {
        calls += "save $slot"
        return result
    }

    override suspend fun setLight(
        action: LightAction,
        source: CommandSource,
    ): BedResult<Unit> {
        calls += "light $action $source"
        return result
    }

    override suspend fun stop(source: CommandSource): BedResult<Unit> {
        calls += "stop $source"
        return result
    }

    override fun holdConnection(): ConnectionLease {
        leaseRequests++
        activeLeases++
        return ConnectionLease { activeLeases-- }
    }
}
