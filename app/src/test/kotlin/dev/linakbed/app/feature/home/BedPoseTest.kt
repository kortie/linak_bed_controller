package dev.linakbed.app.feature.home

import com.google.common.truth.Truth.assertThat
import dev.linakbed.protocol.PresetSlot
import org.junit.Test

class BedPoseTest {
    @Test
    fun `cm maps to a 0 to 1 fraction of the full height`() {
        val pose = BedPoseMapper.from(headCm = 30.0, feetCm = 0.0)

        assertThat(pose.head).isWithin(1e-6f).of(0.5f)
        assertThat(pose.feet).isEqualTo(0f)
        assertThat(pose.known).isTrue()
    }

    @Test
    fun `values beyond the range are clamped`() {
        val pose = BedPoseMapper.from(headCm = BedPoseMapper.HEAD_FULL_CM * 2, feetCm = -5.0)

        assertThat(pose.head).isEqualTo(1f)
        assertThat(pose.feet).isEqualTo(0f)
    }

    @Test
    fun `unknown sensor values stay unknown`() {
        val pose = BedPoseMapper.from(headCm = null, feetCm = 12.0)

        assertThat(pose.head).isNull()
        assertThat(pose.known).isFalse()
    }

    @Test
    fun `ADR-016 preset pictures are fixed - 1 flat, 2 slightly raised, 3 head high`() {
        val flat = presetPicture(PresetSlot.P1)
        val slight = presetPicture(PresetSlot.P2)
        val high = presetPicture(PresetSlot.P3)

        assertThat(flat).isEqualTo(BedPose(head = 0f, feet = 0f))
        assertThat(slight.head).isGreaterThan(0f)
        assertThat(slight.feet).isGreaterThan(0f)
        assertThat(high.head).isGreaterThan(slight.head)
        assertThat(PresetSlot.entries.map(::presetPicture)).isEqualTo(PresetSlot.entries.map(::presetPicture))
        assertThat(listOf(flat, slight, high).all { it.known }).isTrue()
    }
}
