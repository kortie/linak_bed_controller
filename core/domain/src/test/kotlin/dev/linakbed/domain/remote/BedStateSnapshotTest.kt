package dev.linakbed.domain.remote

import com.google.common.truth.Truth.assertThat
import dev.linakbed.domain.controller.ActiveMotion
import dev.linakbed.domain.controller.BedStatus
import dev.linakbed.domain.controller.CommandSource
import dev.linakbed.domain.controller.LightState
import dev.linakbed.domain.controller.MotionKind
import dev.linakbed.domain.port.LinkState
import dev.linakbed.protocol.PresetSlot
import org.junit.jupiter.api.Test

class BedStateSnapshotTest {
    @Test
    fun `snapshot copies status, preset names and the last outcome`() {
        val motion = ActiveMotion(CommandSource.REMOTE, MotionKind.PRESET, PresetSlot.P2)
        val status = BedStatus(link = LinkState.Connected, headCm = 12.4, feetCm = 3.1, activeMotion = motion, light = LightState.ON)
        val names = mapOf(PresetSlot.P1 to "기상")
        val last = RemoteOutcome("req", RemoteResult.Executed)

        val snapshot = status.toSnapshot("bed-1", names, last)

        assertThat(snapshot)
            .isEqualTo(BedStateSnapshot("bed-1", LinkState.Connected, 12.4, 3.1, motion, LightState.ON, names, last))
    }
}
