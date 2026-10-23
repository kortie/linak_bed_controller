package dev.linakbed.domain.controller

import com.google.common.truth.Truth.assertThat
import dev.linakbed.domain.BedError
import dev.linakbed.domain.BedResult
import dev.linakbed.domain.fake.FakeBedLink
import dev.linakbed.domain.fake.FakeBedLogger
import dev.linakbed.domain.fake.FakeClock
import dev.linakbed.domain.fake.FakeExecutionLog
import dev.linakbed.domain.fake.FakePairingStore
import dev.linakbed.domain.port.LinkState
import dev.linakbed.domain.safety.SafetyConfig
import dev.linakbed.domain.safety.SafetyReason
import dev.linakbed.protocol.Actuator
import dev.linakbed.protocol.BedCommand
import dev.linakbed.protocol.CommandTiming
import dev.linakbed.protocol.Direction
import dev.linakbed.protocol.LightAction
import dev.linakbed.protocol.PresetSlot
import dev.linakbed.protocol.ProtocolProfile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.testTimeSource
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultBedControllerTest {
    private val link = FakeBedLink()
    private val pairing = FakePairingStore()
    private val log = FakeExecutionLog()
    private val logger = FakeBedLogger()

    private val headUp = BedCommand.Move(Actuator.HEAD, Direction.UP)
    private val feetDown = BedCommand.Move(Actuator.FEET, Direction.DOWN)

    private fun TestScope.controller(profile: ProtocolProfile = ProtocolProfile.DEFAULT): DefaultBedController =
        DefaultBedController(
            scope = backgroundScope,
            link = link,
            pairingStore = pairing,
            executionLog = log,
            config = BedControllerConfig(profile, SafetyConfig()),
            environment = ControllerEnvironment(FakeClock(), logger, testTimeSource),
        ).also { runCurrent() }

    private fun TestScope.advance(duration: Duration) {
        advanceTimeBy(duration)
        runCurrent()
    }

    /** 센서가 있음을 컨트롤러에 알린다 (S7·프리셋 도착 판단의 전제). */
    private fun TestScope.sensorsPresent() {
        link.emitHeight(Actuator.HEAD, 1000)
        link.emitHeight(Actuator.FEET, 500)
        runCurrent()
    }

    /** 센서 값이 계속 바뀌는 상황 (침대가 움직이는 중). */
    private fun TestScope.keepSensorsMoving() {
        backgroundScope.launch {
            var raw = 1000
            while (true) {
                raw += 10
                link.emitHeight(Actuator.HEAD, raw)
                delay(500.milliseconds)
            }
        }
    }

    private fun writesAfterLastStop() = link.writes.drop(link.writes.lastIndexOf(BedCommand.Stop) + 1)

    @Nested
    inner class Arbitration {
        @Test
        fun `ARB idle - MANUAL move starts after connecting to the paired bed`() =
            runTest {
                val controller = controller()

                val result = controller.startManualMove(Actuator.HEAD, Direction.UP)
                runCurrent()

                assertThat(result).isInstanceOf(BedResult.Ok::class.java)
                assertThat(link.connects).containsExactly(FakePairingStore.DEFAULT.address)
                assertThat(link.writes.first()).isEqualTo(headUp)
                assertThat(controller.status.value.activeMotion).isEqualTo(ActiveMotion(CommandSource.MANUAL, MotionKind.MANUAL_MOVE))
            }

        @Test
        fun `ARB idle - REMOTE preset starts and completes`() =
            runTest {
                val controller = controller()

                val result = async { controller.goToPreset(PresetSlot.P2, CommandSource.REMOTE) }
                advance(SafetyConfig().presetWithoutSensorDuration + 1.seconds)

                assertThat(result.await()).isEqualTo(BedResult.Ok(Unit))
                assertThat(link.writes).contains(BedCommand.RecallPreset(PresetSlot.P2))
                assertThat(link.writes.last()).isEqualTo(BedCommand.Stop)
                assertThat(controller.status.value.activeMotion).isNull()
            }

        @Test
        fun `ARB MANUAL active - new MANUAL move replaces it after a Stop`() =
            runTest {
                val controller = controller()
                controller.startManualMove(Actuator.HEAD, Direction.UP)
                advance(400.milliseconds)

                val second = controller.startManualMove(Actuator.FEET, Direction.DOWN)
                runCurrent()

                assertThat(second).isInstanceOf(BedResult.Ok::class.java)
                val stopIndex = link.writes.indexOf(BedCommand.Stop)
                assertThat(stopIndex).isGreaterThan(0)
                assertThat(link.writes.take(stopIndex).toSet()).containsExactly(headUp)
                assertThat(link.writes.drop(stopIndex + 1).toSet()).containsExactly(feetDown)
            }

        @Test
        fun `ARB MANUAL active - REMOTE preset is rejected with Busy(MANUAL)`() =
            runTest {
                val controller = controller()
                controller.startManualMove(Actuator.HEAD, Direction.UP)
                runCurrent()

                val result = controller.goToPreset(PresetSlot.P1, CommandSource.REMOTE)

                assertThat(result).isEqualTo(BedResult.Err(BedError.Busy(CommandSource.MANUAL)))
                assertThat(link.writes).doesNotContain(BedCommand.RecallPreset(PresetSlot.P1))
                assertThat(
                    controller.status.value.activeMotion
                        ?.source,
                ).isEqualTo(CommandSource.MANUAL)
            }

        @Test
        fun `ARB REMOTE active - MANUAL move interrupts it (person first)`() =
            runTest {
                val controller = controller()
                val preset = async { controller.goToPreset(PresetSlot.P1, CommandSource.REMOTE) }
                advance(400.milliseconds)

                val manual = controller.startManualMove(Actuator.HEAD, Direction.UP)
                runCurrent()

                assertThat(manual).isInstanceOf(BedResult.Ok::class.java)
                assertThat(preset.await()).isEqualTo(BedResult.Err(BedError.Interrupted(CommandSource.MANUAL)))
                val stopIndex = link.writes.indexOf(BedCommand.Stop)
                assertThat(link.writes.drop(stopIndex + 1).toSet()).containsExactly(headUp)
            }

        @Test
        fun `ARB REMOTE active - newer REMOTE preset replaces it`() =
            runTest {
                val controller = controller()
                val first = async { controller.goToPreset(PresetSlot.P1, CommandSource.REMOTE) }
                advance(400.milliseconds)

                val second = async { controller.goToPreset(PresetSlot.P2, CommandSource.REMOTE) }
                runCurrent()

                assertThat(first.await()).isEqualTo(BedResult.Err(BedError.Interrupted(CommandSource.REMOTE)))
                assertThat(
                    controller.status.value.activeMotion
                        ?.preset,
                ).isEqualTo(PresetSlot.P2)
                advance(SafetyConfig().presetWithoutSensorDuration + 1.seconds)
                assertThat(second.await()).isEqualTo(BedResult.Ok(Unit))
            }

        @Test
        fun `ARB STOP - ends a REMOTE preset with a single Stop`() =
            runTest {
                val controller = controller()
                val preset = async { controller.goToPreset(PresetSlot.P3, CommandSource.REMOTE) }
                advance(400.milliseconds)

                val stopped = controller.stop(CommandSource.MANUAL)

                assertThat(stopped).isEqualTo(BedResult.Ok(Unit))
                assertThat(preset.await()).isEqualTo(BedResult.Err(BedError.Interrupted(CommandSource.MANUAL)))
                assertThat(link.writes.count { it == BedCommand.Stop }).isEqualTo(1)
                assertThat(link.writes.last()).isEqualTo(BedCommand.Stop)
            }
    }

    @Nested
    inner class SafetyRules {
        @Test
        fun `S1 manual move auto-stops after 30s`() =
            runTest {
                val controller = controller()
                controller.startManualMove(Actuator.HEAD, Direction.UP)

                advance(29.seconds)
                assertThat(controller.status.value.activeMotion).isNotNull()
                advance(1.seconds)

                assertThat(controller.status.value.activeMotion).isNull()
                assertThat(controller.status.value.lastError).isEqualTo(BedError.SafetyStop(SafetyReason.MAX_DURATION))
                assertThat(link.writes.last()).isEqualTo(BedCommand.Stop)
                assertThat(log.entries.last().result).isEqualTo("safety_stop:MAX_DURATION")
            }

        @Test
        fun `S1 preset auto-stops after 60s even while the bed keeps moving`() =
            runTest {
                val controller = controller()
                sensorsPresent()
                keepSensorsMoving()

                val result = async { controller.goToPreset(PresetSlot.P1, CommandSource.REMOTE) }
                advance(60.seconds)

                assertThat(result.await()).isEqualTo(BedResult.Err(BedError.SafetyStop(SafetyReason.MAX_DURATION)))
                assertThat(link.writes.last()).isEqualTo(BedCommand.Stop)
            }

        @Test
        fun `S2 Stop is retried three more times when it fails`() =
            runTest {
                val controller = controller()
                controller.startManualMove(Actuator.HEAD, Direction.UP)
                runCurrent()
                link.writeResult = { if (it == BedCommand.Stop) BedResult.Err(BedError.WriteFailed(1)) else BedResult.Ok(Unit) }

                val result = async { controller.stop(CommandSource.MANUAL) }
                advance(1.seconds)

                assertThat(result.await()).isEqualTo(BedResult.Err(BedError.WriteFailed(1)))
                assertThat(link.writes.count { it == BedCommand.Stop }).isEqualTo(4)
                assertThat(controller.status.value.activeMotion).isNull()
            }

        @Test
        fun `S2 Stop is not retried when there is no connection`() =
            runTest {
                val controller = controller()

                assertThat(controller.stop(CommandSource.MANUAL)).isEqualTo(BedResult.Err(BedError.Disconnected))
                assertThat(link.writes).isEmpty()
            }

        @Test
        fun `S3 release sends Stop right away and no further moves`() =
            runTest {
                val controller = controller()
                val handle = (controller.startManualMove(Actuator.HEAD, Direction.UP) as BedResult.Ok).value
                advance(600.milliseconds)

                handle.release()
                runCurrent()
                assertThat(link.writes.last()).isEqualTo(BedCommand.Stop)

                advance(2.seconds)
                assertThat(writesAfterLastStop()).isEmpty()
                assertThat(log.entries.single().result).isEqualTo("released")
            }

        @Test
        fun `S5 link loss during a preset stops without reconnecting`() =
            runTest {
                val controller = controller()
                val result = async { controller.goToPreset(PresetSlot.P2, CommandSource.REMOTE) }
                advance(400.milliseconds)

                link.dropLink()
                runCurrent()

                assertThat(result.await()).isEqualTo(BedResult.Err(BedError.SafetyStop(SafetyReason.DISCONNECTED)))
                advance(5.seconds)
                assertThat(link.connects).hasSize(1)
                assertThat(log.entries.single().source).isEqualTo(CommandSource.REMOTE)
                assertThat(log.entries.single().result).isEqualTo("safety_stop:DISCONNECTED")
            }

        @Test
        fun `S5 link loss during a manual move is a safety stop`() =
            runTest {
                val controller = controller()
                controller.startManualMove(Actuator.HEAD, Direction.UP)
                advance(400.milliseconds)

                link.dropLink()
                runCurrent()

                assertThat(controller.status.value.lastError).isEqualTo(BedError.SafetyStop(SafetyReason.DISCONNECTED))
                assertThat(controller.status.value.activeMotion).isNull()
            }

        @Test
        fun `S5 connection failure before starting is retried once`() =
            runTest {
                val controller = controller()
                link.connectResults += BedResult.Err(BedError.ConnectTimeout)

                val result = controller.startManualMove(Actuator.HEAD, Direction.UP)

                assertThat(result).isInstanceOf(BedResult.Ok::class.java)
                assertThat(link.connects).hasSize(2)
            }

        @Test
        fun `S5 two connection failures give up without moving`() =
            runTest {
                val controller = controller()
                link.connectResults += BedResult.Err(BedError.ConnectTimeout)
                link.connectResults += BedResult.Err(BedError.ConnectTimeout)

                val result = controller.startManualMove(Actuator.HEAD, Direction.UP)

                assertThat(result).isEqualTo(BedResult.Err(BedError.ConnectTimeout))
                assertThat(link.writes).isEmpty()
                assertThat(log.entries.single().result).isEqualTo("error:ConnectTimeout")
            }

        @Test
        fun `S5 missing permission is not retried`() =
            runTest {
                val controller = controller()
                link.connectResults += BedResult.Err(BedError.PermissionMissing("p"))

                controller.goToPreset(PresetSlot.P1, CommandSource.REMOTE)

                assertThat(link.connects).hasSize(1)
                assertThat(log.entries).isEmpty()
            }

        @Test
        fun `S7 manual move stops when sensors stop changing for 3s`() =
            runTest {
                val controller = controller()
                sensorsPresent()
                controller.startManualMove(Actuator.HEAD, Direction.UP)

                advance(2.9.seconds)
                assertThat(controller.status.value.activeMotion).isNotNull()
                advance(200.milliseconds)

                assertThat(controller.status.value.lastError).isEqualTo(BedError.SafetyStop(SafetyReason.STALL))
                assertThat(link.writes.last()).isEqualTo(BedCommand.Stop)
            }

        @Test
        fun `S7 manual move continues while sensors keep changing`() =
            runTest {
                val controller = controller()
                sensorsPresent()
                keepSensorsMoving()
                controller.startManualMove(Actuator.HEAD, Direction.UP)

                advance(10.seconds)

                assertThat(controller.status.value.activeMotion).isNotNull()
                assertThat(controller.status.value.lastError).isNull()
            }

        @Test
        fun `S7 does not apply without sensors - only S1 stops the move`() =
            runTest {
                val controller = controller()
                controller.startManualMove(Actuator.HEAD, Direction.UP)

                advance(10.seconds)

                assertThat(controller.status.value.activeMotion).isNotNull()
            }
    }

    @Nested
    inner class PresetCompletion {
        @Test
        fun `preset completes once sensors settle for 1_5s after moving`() =
            runTest {
                val controller = controller()
                sensorsPresent()

                val result = async { controller.goToPreset(PresetSlot.P1, CommandSource.MANUAL) }
                runCurrent()
                // t=0, 0.5, 1.0, 1.5s에 값이 바뀌고 t=2.0s에서 루프가 끝난다 → 마지막 변화는 0.5s 전.
                repeat(4) {
                    link.emitHeight(Actuator.HEAD, 1100 + it * 10)
                    advance(500.milliseconds)
                }
                advance(900.milliseconds) // 마지막 변화 후 1.4s
                assertThat(result.isCompleted).isFalse()
                advance(200.milliseconds) // 1.6s ≥ 1.5s

                assertThat(result.await()).isEqualTo(BedResult.Ok(Unit))
                assertThat(link.writes.last()).isEqualTo(BedCommand.Stop)
                assertThat(log.entries.single().action).isEqualTo("preset P1")
                assertThat(log.entries.single().result).isEqualTo("ok")
            }

        @Test
        fun `preset with no movement completes after 3s`() =
            runTest {
                val controller = controller()
                sensorsPresent()

                val result = async { controller.goToPreset(PresetSlot.P4, CommandSource.REMOTE) }
                advance(3.1.seconds)

                assertThat(result.await()).isEqualTo(BedResult.Ok(Unit))
            }

        @Test
        fun `preset without sensors completes after the fixed wait`() =
            runTest {
                val controller = controller()

                val result = async { controller.goToPreset(PresetSlot.P1, CommandSource.REMOTE) }
                advance(29.seconds)
                assertThat(result.isCompleted).isFalse()
                advance(1.1.seconds)

                assertThat(result.await()).isEqualTo(BedResult.Ok(Unit))
            }

        @Test
        fun `write failure ends the preset with that error`() =
            runTest {
                val controller = controller()
                link.writeResult = { if (it is BedCommand.RecallPreset) BedResult.Err(BedError.WriteFailed(201)) else BedResult.Ok(Unit) }

                val result = controller.goToPreset(PresetSlot.P1, CommandSource.REMOTE)

                assertThat(result).isEqualTo(BedResult.Err(BedError.WriteFailed(201)))
                assertThat(link.writes.last()).isEqualTo(BedCommand.Stop)
            }
    }

    @Nested
    inner class Timing {
        @Test
        fun `Repeat 200ms writes the move about five times per second`() =
            runTest {
                val controller = controller()
                controller.startManualMove(Actuator.HEAD, Direction.UP)
                runCurrent()

                advance(1.seconds)

                assertThat(link.writes.count { it == headUp }).isIn(5..6)
            }

        @Test
        fun `OneShot writes the move once`() =
            runTest {
                val controller = controller(ProtocolProfile.DEFAULT.copy(moveTiming = CommandTiming.OneShot))
                controller.startManualMove(Actuator.HEAD, Direction.UP)

                advance(1.seconds)

                assertThat(link.writes).containsExactly(headUp)
            }
    }

    @Nested
    inner class ConnectionAndIdle {
        @Test
        fun `not paired fails without connecting`() =
            runTest {
                pairing.device = null
                val controller = controller()

                assertThat(controller.goToPreset(PresetSlot.P1, CommandSource.MANUAL)).isEqualTo(BedResult.Err(BedError.NotPaired))
                assertThat(link.connects).isEmpty()
            }

        @Test
        fun `disconnects after 60s idle`() =
            runTest {
                val controller = controller()
                val handle = (controller.startManualMove(Actuator.HEAD, Direction.UP) as BedResult.Ok).value
                handle.release()
                runCurrent()

                advance(59.seconds)
                assertThat(link.disconnects).isEqualTo(0)
                advance(1.seconds)

                assertThat(link.disconnects).isEqualTo(1)
            }

        @Test
        fun `a held connection lease prevents the idle disconnect`() =
            runTest {
                val controller = controller()
                val lease = controller.holdConnection()
                runCurrent()
                assertThat(link.connects).hasSize(1)

                controller.setLight(LightAction.ON, CommandSource.MANUAL)
                advance(5.seconds * 60)
                assertThat(link.disconnects).isEqualTo(0)

                lease.release()
                lease.release()
                advance(60.seconds)
                assertThat(link.disconnects).isEqualTo(1)
            }
    }

    @Nested
    inner class LightAndRecords {
        @Test
        fun `light is not arbitrated - works during a manual move`() =
            runTest {
                val controller = controller()
                controller.startManualMove(Actuator.HEAD, Direction.UP)
                runCurrent()

                val result = controller.setLight(LightAction.ON, CommandSource.REMOTE)

                assertThat(result).isEqualTo(BedResult.Ok(Unit))
                assertThat(link.writes).contains(BedCommand.Light(LightAction.ON))
                assertThat(
                    controller.status.value.activeMotion
                        ?.source,
                ).isEqualTo(CommandSource.MANUAL)
            }

        @Test
        fun `light state follows the last successful command and resets on disconnect`() =
            runTest {
                val controller = controller()

                controller.setLight(LightAction.ON, CommandSource.MANUAL)
                assertThat(controller.status.value.light).isEqualTo(LightState.ON)
                controller.setLight(LightAction.OFF, CommandSource.MANUAL)
                assertThat(controller.status.value.light).isEqualTo(LightState.OFF)
                controller.setLight(LightAction.TOGGLE, CommandSource.MANUAL)
                assertThat(controller.status.value.light).isEqualTo(LightState.UNKNOWN)

                controller.setLight(LightAction.ON, CommandSource.MANUAL)
                runCurrent() // 컨트롤러가 '연결됨'을 관찰할 기회 (StateFlow는 같은 값을 합친다)
                link.dropLink()
                runCurrent()
                assertThat(controller.status.value.light).isEqualTo(LightState.UNKNOWN)
            }

        @Test
        fun `failed light write keeps the previous light state`() =
            runTest {
                val controller = controller()
                controller.setLight(LightAction.ON, CommandSource.MANUAL)
                link.writeResult = { BedResult.Err(BedError.WriteFailed(3)) }

                controller.setLight(LightAction.OFF, CommandSource.MANUAL)

                assertThat(controller.status.value.light).isEqualTo(LightState.ON)
            }

        @Test
        fun `REMOTE results are not recorded here but MANUAL ones are`() =
            runTest {
                val controller = controller()

                controller.setLight(LightAction.ON, CommandSource.REMOTE)
                controller.setLight(LightAction.OFF, CommandSource.MANUAL)
                controller.stop(CommandSource.REMOTE)
                controller.stop(CommandSource.MANUAL)

                assertThat(log.entries.map { it.source to it.action })
                    .containsExactly(CommandSource.MANUAL to "light OFF", CommandSource.MANUAL to "stop")
                    .inOrder()
            }

        @Test
        fun `save preset writes once, is recorded and replaces a REMOTE preset`() =
            runTest {
                val controller = controller()
                val preset = async { controller.goToPreset(PresetSlot.P1, CommandSource.REMOTE) }
                advance(400.milliseconds)

                val saved = controller.savePreset(PresetSlot.P3)

                assertThat(saved).isEqualTo(BedResult.Ok(Unit))
                assertThat(preset.await()).isEqualTo(BedResult.Err(BedError.Interrupted(CommandSource.MANUAL)))
                assertThat(link.writes.count { it == BedCommand.SavePreset(PresetSlot.P3) }).isEqualTo(1)
                assertThat(link.writes.last()).isEqualTo(BedCommand.SavePreset(PresetSlot.P3))
                assertThat(log.entries.last().action).isEqualTo("save_preset P3")
            }

        @Test
        fun `status follows link state and sensor readings`() =
            runTest {
                val controller = controller()
                controller.holdConnection()
                runCurrent()

                link.emitHeight(Actuator.HEAD, 1000)
                link.emitHeight(Actuator.FEET, 500)
                runCurrent()

                assertThat(controller.status.value.link).isEqualTo(LinkState.Connected)
                assertThat(controller.status.value.headCm).isEqualTo(40.0)
                assertThat(controller.status.value.feetCm).isEqualTo(20.0)
            }

        @Test
        fun `failed background connect is shown as lastError`() =
            runTest {
                pairing.device = null
                val controller = controller()

                controller.holdConnection()
                runCurrent()

                assertThat(controller.status.value.lastError).isEqualTo(BedError.NotPaired)
            }
    }
}
