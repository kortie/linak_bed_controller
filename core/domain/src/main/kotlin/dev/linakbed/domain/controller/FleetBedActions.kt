package dev.linakbed.domain.controller

import dev.linakbed.domain.BedError
import dev.linakbed.domain.BedResult
import dev.linakbed.domain.port.LinkState
import dev.linakbed.protocol.LightAction
import dev.linakbed.protocol.PresetSlot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/**
 * 모든 침대에 같은 동작을 동시에 보낸다 (ADR-012): 원격 명령(같은 프리셋 번호·정지·조명)과 전체 STOP.
 * 결과는 모두 성공해야 성공, 아니면 첫 실패. 침대가 없으면 [BedError.NotPaired].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FleetBedActions(
    private val beds: StateFlow<List<BedUnit>>,
    scope: CoroutineScope,
) : BedActions {
    override val status: StateFlow<BedStatus> =
        beds
            .flatMapLatest { list ->
                if (list.isEmpty()) flowOf(BedStatus()) else combine(list.map { it.controller.status }) { merge(it.toList()) }
            }.stateIn(scope, SharingStarted.Eagerly, BedStatus())

    override suspend fun goToPreset(
        slot: PresetSlot,
        source: CommandSource,
    ): BedResult<Unit> = onAll { it.goToPreset(slot, source) }

    override suspend fun setLight(
        action: LightAction,
        source: CommandSource,
    ): BedResult<Unit> = onAll { it.setLight(action, source) }

    override suspend fun stop(source: CommandSource): BedResult<Unit> = onAll { it.stop(source) }

    private suspend fun onAll(action: suspend (BedController) -> BedResult<Unit>): BedResult<Unit> {
        val controllers = beds.value.map { it.controller }
        if (controllers.isEmpty()) return BedResult.Err(BedError.NotPaired)
        val results = coroutineScope { controllers.map { async { action(it) } }.awaitAll() }
        return results.firstOrNull { it is BedResult.Err } ?: BedResult.Ok(Unit)
    }

    companion object {
        /**
         * 침대들의 상태를 하나로: 동작은 수동 조작이 있으면 그것(S9 대기 중 수동 조작 감지), 연결은 모두 연결돼야 연결됨,
         * 오류는 처음 것. 높이·조명은 첫 침대 기준.
         */
        fun merge(statuses: List<BedStatus>): BedStatus {
            val first = statuses.first()
            val motions = statuses.mapNotNull { it.activeMotion }
            return first.copy(
                link =
                    when {
                        statuses.all { it.link == LinkState.Connected } -> LinkState.Connected
                        statuses.any { it.link == LinkState.Connecting } -> LinkState.Connecting
                        else -> statuses.first { it.link != LinkState.Connected }.link
                    },
                activeMotion = motions.firstOrNull { it.source == CommandSource.MANUAL } ?: motions.firstOrNull(),
                lastError = statuses.firstNotNullOfOrNull { it.lastError },
            )
        }
    }
}
