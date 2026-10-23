package dev.linakbed.app.simbed

import com.google.common.truth.Truth.assertThat
import dev.linakbed.domain.BedError
import dev.linakbed.domain.BedResult
import dev.linakbed.domain.controller.BedControllerConfig
import dev.linakbed.domain.controller.CommandSource
import dev.linakbed.domain.controller.ControllerEnvironment
import dev.linakbed.domain.controller.DefaultBedController
import dev.linakbed.domain.fake.FakeBedLogger
import dev.linakbed.domain.fake.FakeClock
import dev.linakbed.domain.fake.FakeExecutionLog
import dev.linakbed.domain.fake.FakePairingStore
import dev.linakbed.domain.safety.SafetyConfig
import dev.linakbed.domain.safety.SafetyReason
import dev.linakbed.protocol.Actuator
import dev.linakbed.protocol.BedCommand
import dev.linakbed.protocol.Direction
import dev.linakbed.protocol.PresetSlot
import dev.linakbed.protocol.ProtocolProfile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.testTimeSource
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

/** 실제 컨트롤러·안전 감독이 가상 침대와 맞물려 도는지 (침대 없이 앱을 시험할 수 있다는 근거). */
@OptIn(ExperimentalCoroutinesApi::class)
class SimulatedBedControllerTest {
    private val pairing = FakePairingStore(device = SimBedViewModel.DEVICE)

    private fun TestScope.setUp(): Pair<SimulatedBedLink, DefaultBedController> {
        val link = SimulatedBedLink(ProtocolProfile.DEFAULT, backgroundScope, FakeBedLogger())
        val controller =
            DefaultBedController(
                scope = backgroundScope,
                link = link,
                pairingStore = pairing,
                executionLog = FakeExecutionLog(),
                config = BedControllerConfig(ProtocolProfile.DEFAULT, SafetyConfig()),
                environment = ControllerEnvironment(FakeClock(), FakeBedLogger(), testTimeSource),
            )
        runCurrent()
        return link to controller
    }

    @Test
    fun `preset recall arrives at the preset position`() =
        runTest {
            val (link, controller) = setUp()

            val result = controller.goToPreset(PresetSlot.P2, CommandSource.REMOTE)

            assertThat(result).isEqualTo(BedResult.Ok(Unit))
            assertThat(link.snapshot.value.position).isEqualTo(SimulatedBed.DEFAULT_PRESETS.getValue(PresetSlot.P2))
            assertThat(
                link.received.value
                    .first()
                    .command,
            ).isEqualTo(BedCommand.Stop)
        }

    @Test
    fun `manual move raises the head while held and stops on release`() =
        runTest {
            val (link, controller) = setUp()

            val handle = (controller.startManualMove(Actuator.HEAD, Direction.UP) as BedResult.Ok).value
            advanceTimeBy(2.seconds)
            handle.release()
            advanceTimeBy(1.seconds)
            val stopped = link.snapshot.value.position.head
            advanceTimeBy(2.seconds)

            assertThat(stopped).isGreaterThan(0)
            assertThat(link.snapshot.value.position.head).isEqualTo(stopped)
            assertThat(controller.status.value.headCm).isEqualTo(stopped / 5 / 5.0)
        }

    @Test
    fun `S5 dropping the link during a preset is a safety stop`() =
        runTest {
            val (link, controller) = setUp()

            val preset = async { controller.goToPreset(PresetSlot.P3, CommandSource.MANUAL) }
            advanceTimeBy(2.seconds)
            link.dropLink()

            assertThat(preset.await()).isEqualTo(BedResult.Err(BedError.SafetyStop(SafetyReason.DISCONNECTED)))
        }

    @Test
    fun `connect failure surfaces as ConnectTimeout`() =
        runTest {
            val (link, controller) = setUp()
            link.failConnect.value = true

            val result = controller.goToPreset(PresetSlot.P1, CommandSource.MANUAL)

            assertThat(result).isEqualTo(BedResult.Err(BedError.ConnectTimeout))
        }
}
