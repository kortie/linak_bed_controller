package dev.linakbed.domain.controller

import com.google.common.truth.Truth.assertThat
import dev.linakbed.domain.BedError
import dev.linakbed.domain.BedResult
import dev.linakbed.domain.fake.FakeBedController
import dev.linakbed.domain.fake.FakeBedLink
import dev.linakbed.domain.fake.FakeBedLogger
import dev.linakbed.domain.fake.FakePairingStore
import dev.linakbed.domain.fake.FakePresetPoseStore
import dev.linakbed.domain.model.PairedDevice
import dev.linakbed.domain.port.LinkState
import dev.linakbed.domain.port.PairingStore
import dev.linakbed.domain.preset.PresetPose
import dev.linakbed.protocol.LightAction
import dev.linakbed.protocol.PresetSlot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/** ADR-012: 침대별 컨트롤러, 원격·STOP은 모든 침대. */
@OptIn(ExperimentalCoroutinesApi::class)
class FleetTest {
    private val store = FakePairingStore(device = null)
    private val poses = FakePresetPoseStore()
    private val controllers = mutableMapOf<String, FakeBedController>()
    private val links = mutableMapOf<String, ReadingLink>()

    /** 프리셋 위치를 읽어 주는 가짜 링크. */
    private class ReadingLink(
        val presets: Map<PresetSlot, PresetPose>?,
    ) : dev.linakbed.domain.port.BedLink by FakeBedLink() {
        var disconnects = 0

        override suspend fun disconnect() {
            disconnects++
        }

        override suspend fun readPresetPoses() = presets
    }

    private fun TestScope.fleet(presets: Map<PresetSlot, PresetPose>? = null): BedFleet {
        val factory =
            BedPartsFactory { device, _ ->
                val link = ReadingLink(presets).also { links[device.bedId] = it }
                BedParts(link, FakeBedController().also { controllers[device.bedId] = it })
            }
        return BedFleet(backgroundScope, store, factory, poses, FakeBedLogger()).also { runCurrent() }
    }

    @Test
    fun `beds follow the pairing list - added, found by id, removed and disconnected`() =
        runTest {
            val fleet = fleet()
            assertThat(fleet.beds.value).isEmpty()

            store.save(FakePairingStore.DEFAULT)
            store.save(FakePairingStore.SECOND)
            runCurrent()
            assertThat(fleet.beds.value.map { it.device.bedId }).containsExactly("bed-1", "bed-2").inOrder()
            assertThat(fleet.controller("bed-2")).isNotNull()

            store.remove("bed-1")
            runCurrent()
            assertThat(fleet.beds.value.map { it.device.bedId }).containsExactly("bed-2")
            assertThat(fleet.controller("bed-1")).isNull()
            assertThat(links.getValue("bed-1").disconnects).isEqualTo(1)
        }

    @Test
    fun `renaming a bed keeps its controller`() =
        runTest {
            val fleet = fleet()
            store.save(FakePairingStore.DEFAULT)
            runCurrent()
            val before = fleet.controller("bed-1")

            store.save(FakePairingStore.DEFAULT.copy(name = "안방"))
            runCurrent()

            assertThat(fleet.controller("bed-1")).isSameInstanceAs(before)
            assertThat(
                fleet.beds.value
                    .single()
                    .device.name,
            ).isEqualTo("안방")
        }

    @Test
    fun `remote preset, light and stop go to every bed`() =
        runTest {
            val fleet = fleet()
            store.save(FakePairingStore.DEFAULT)
            store.save(FakePairingStore.SECOND)
            runCurrent()

            assertThat(fleet.all.goToPreset(PresetSlot.P2, CommandSource.REMOTE)).isEqualTo(BedResult.Ok(Unit))
            fleet.all.setLight(LightAction.ON, CommandSource.REMOTE)
            fleet.all.stop(CommandSource.MANUAL)

            controllers.values.forEach {
                assertThat(it.calls).containsExactly("preset P2 REMOTE", "light ON REMOTE", "stop MANUAL").inOrder()
            }
        }

    @Test
    fun `one failure makes the whole command fail, no beds is NotPaired`() =
        runTest {
            val fleet = fleet()
            assertThat(fleet.all.stop(CommandSource.MANUAL)).isEqualTo(BedResult.Err(BedError.NotPaired))

            store.save(FakePairingStore.DEFAULT)
            store.save(FakePairingStore.SECOND)
            runCurrent()
            controllers.getValue("bed-2").result = BedResult.Err(BedError.ConnectTimeout)

            assertThat(fleet.all.goToPreset(PresetSlot.P1, CommandSource.REMOTE)).isEqualTo(BedResult.Err(BedError.ConnectTimeout))
            assertThat(controllers.getValue("bed-1").calls).containsExactly("preset P1 REMOTE")
        }

    @Test
    fun `merged status prefers a manual motion and is connected only when all are`() =
        runTest {
            val fleet = fleet()
            store.save(FakePairingStore.DEFAULT)
            store.save(FakePairingStore.SECOND)
            runCurrent()

            controllers.getValue("bed-1").status.value =
                BedStatus(link = LinkState.Connected, activeMotion = ActiveMotion(CommandSource.REMOTE, MotionKind.PRESET, PresetSlot.P2))
            controllers.getValue("bed-2").status.value =
                BedStatus(link = LinkState.Disconnected, activeMotion = ActiveMotion(CommandSource.MANUAL, MotionKind.MANUAL_MOVE))
            runCurrent()

            assertThat(
                fleet.all.status.value.activeMotion
                    ?.source,
            ).isEqualTo(CommandSource.MANUAL)
            assertThat(fleet.all.status.value.link).isEqualTo(LinkState.Disconnected)
        }

    @Test
    fun `preset values the bed can report are recorded when it connects`() =
        runTest {
            val presets = mapOf(PresetSlot.P2 to PresetPose(30.0, 10.0))
            fleet(presets)
            store.save(FakePairingStore.DEFAULT)
            runCurrent()
            assertThat(poses.of("bed-1")).isEmpty()

            controllers.getValue("bed-1").status.value = BedStatus(link = LinkState.Connected)
            runCurrent()

            assertThat(poses.of("bed-1")).isEqualTo(presets)
        }

    @Test
    fun `pairing store keeps at most two beds and replaces by id or address`() {
        val a = FakePairingStore.DEFAULT
        val b = FakePairingStore.SECOND
        val c = PairedDevice("bed-3", "77:77:77:77:77:77", null, 3)

        assertThat(PairingStore.merged(listOf(a), a.copy(name = "x"))).containsExactly(a.copy(name = "x"))
        assertThat(PairingStore.merged(listOf(a), a.copy(bedId = "new", address = a.address.lowercase()))!!.single().bedId).isEqualTo("new")
        assertThat(PairingStore.merged(listOf(a), b)).containsExactly(a, b).inOrder()
        assertThat(PairingStore.merged(listOf(a, b), c)).isNull()
    }
}
