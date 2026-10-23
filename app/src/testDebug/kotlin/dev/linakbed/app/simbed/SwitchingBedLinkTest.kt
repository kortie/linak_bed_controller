package dev.linakbed.app.simbed

import com.google.common.truth.Truth.assertThat
import dev.linakbed.domain.fake.FakeBedLink
import dev.linakbed.domain.fake.FakeBedLogger
import dev.linakbed.domain.port.LinkState
import dev.linakbed.protocol.BedCommand
import dev.linakbed.protocol.ProtocolProfile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class SwitchingBedLinkTest {
    @Test
    fun `the simulated address goes to the virtual bed, others to the real link`() =
        runTest {
            val real = FakeBedLink()
            val simulated = SimulatedBedLink(ProtocolProfile.DEFAULT, backgroundScope, FakeBedLogger())
            val link = SwitchingBedLink(real, simulated, backgroundScope)

            link.connect(SimulatedBedLink.ADDRESS, 10.seconds)
            link.write(BedCommand.Stop)
            runCurrent()

            assertThat(real.connects).isEmpty()
            assertThat(
                simulated.received.value
                    .single()
                    .command,
            ).isEqualTo(BedCommand.Stop)
            assertThat(link.state.value).isEqualTo(LinkState.Connected)

            link.connect("AA:BB:CC:DD:EE:FF", 10.seconds)
            link.write(BedCommand.Stop)
            runCurrent()

            assertThat(simulated.state.value).isEqualTo(LinkState.Disconnected)
            assertThat(real.connects).containsExactly("AA:BB:CC:DD:EE:FF")
            assertThat(real.writes).containsExactly(BedCommand.Stop)
            assertThat(link.state.value).isEqualTo(real.state.value)
        }

    @Test
    fun `telemetry follows the active link`() =
        runTest {
            val real = FakeBedLink()
            val simulated = SimulatedBedLink(ProtocolProfile.DEFAULT, backgroundScope, FakeBedLogger())
            val link = SwitchingBedLink(real, simulated, backgroundScope)
            val first = backgroundScope.launch { link.telemetry.first() }
            runCurrent()

            link.connect(SimulatedBedLink.ADDRESS, 10.seconds)
            runCurrent()

            assertThat(first.isCompleted).isTrue()
        }
}
