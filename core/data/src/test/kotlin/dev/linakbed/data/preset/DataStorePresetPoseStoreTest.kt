package dev.linakbed.data.preset

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.google.common.truth.Truth.assertThat
import dev.linakbed.domain.preset.PresetPose
import dev.linakbed.protocol.PresetSlot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DataStorePresetPoseStoreTest {
    @get:Rule
    val temp = TemporaryFolder()

    private fun TestScope.store() =
        DataStorePresetPoseStore(PreferenceDataStoreFactory.create(scope = backgroundScope) { File(temp.root, "p.preferences_pb") })

    @Test
    fun `nothing is known at first`() =
        runTest {
            assertThat(store().poses("bed-1").first()).isEmpty()
        }

    @Test
    fun `records per bed and slot, overwrites, beds are independent`() =
        runTest {
            val store = store()

            store.record("bed-1", PresetSlot.P1, PresetPose(10.0, 0.0))
            store.record("bed-1", PresetSlot.P4, PresetPose(30.0, 15.0))
            store.record("bed-1", PresetSlot.P4, PresetPose(35.5, 20.25))
            store.record("bed-2", PresetSlot.P1, PresetPose(50.0, 5.0))

            assertThat(store.poses("bed-1").first())
                .containsExactly(PresetSlot.P1, PresetPose(10.0, 0.0), PresetSlot.P4, PresetPose(35.5, 20.25))
            assertThat(store.poses("bed-2").first()).containsExactly(PresetSlot.P1, PresetPose(50.0, 5.0))
        }
}
