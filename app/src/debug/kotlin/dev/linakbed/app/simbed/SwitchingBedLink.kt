package dev.linakbed.app.simbed

import dev.linakbed.domain.BedResult
import dev.linakbed.domain.port.BedLink
import dev.linakbed.domain.port.LinkState
import dev.linakbed.protocol.BedCommand
import dev.linakbed.protocol.HeightReading
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlin.time.Duration

/**
 * 디버그 빌드의 [BedLink]: 연결 주소가 [SimulatedBedLink.ADDRESS]면 가상 침대, 아니면 실제 BLE 링크로 보낸다.
 * 상태·센서는 지금 고른 쪽을 따른다. 릴리스 빌드에는 없다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SwitchingBedLink(
    private val real: BedLink,
    private val simulated: SimulatedBedLink,
    scope: CoroutineScope,
) : BedLink {
    private val active = MutableStateFlow(real)

    override val state: StateFlow<LinkState> =
        active.flatMapLatest { it.state }.stateIn(scope, SharingStarted.Eagerly, real.state.value)

    override val telemetry: Flow<HeightReading> = active.flatMapLatest { it.telemetry }

    override suspend fun connect(
        address: String,
        timeout: Duration,
    ): BedResult<Unit> {
        val target = if (address == SimulatedBedLink.ADDRESS) simulated else real
        if (active.value !== target) {
            active.value.disconnect()
            active.value = target
        }
        return target.connect(address, timeout)
    }

    override suspend fun disconnect() = active.value.disconnect()

    override suspend fun write(command: BedCommand): BedResult<Unit> = active.value.write(command)
}
