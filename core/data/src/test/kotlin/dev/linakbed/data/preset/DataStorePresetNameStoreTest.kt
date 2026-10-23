package dev.linakbed.data.preset

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.google.common.truth.Truth.assertThat
import dev.linakbed.protocol.PresetSlot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class DataStorePresetNameStoreTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val file get() = File(temp.root, "preset_names.preferences_pb")

    private fun TestScope.store() = DataStorePresetNameStore(PreferenceDataStoreFactory.create(scope = backgroundScope) { file })

    @Test
    fun `empty store returns default names for all four slots`() =
        runTest {
            assertThat(store().names().first())
                .containsExactly(PresetSlot.P1, "프리셋 1", PresetSlot.P2, "프리셋 2", PresetSlot.P3, "프리셋 3", PresetSlot.P4, "프리셋 4")
        }

    @Test
    fun `rename is trimmed, emitted and other slots keep defaults`() =
        runTest {
            val store = store()
            val emissions = mutableListOf<Map<PresetSlot, String>>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { store.names().toList(emissions) }

            store.rename(PresetSlot.P2, "  기상 ")

            assertThat(emissions.last()[PresetSlot.P2]).isEqualTo("기상")
            assertThat(emissions.last()[PresetSlot.P1]).isEqualTo("프리셋 1")
            assertThat(emissions.size).isAtLeast(2)
        }

    @Test
    fun `names persist across store instances`() {
        // 각 runTest가 끝나면 DataStore 스코프가 닫힌다 → 같은 파일로 새 인스턴스를 연다 (앱 재시작과 같음).
        runTest { store().rename(PresetSlot.P4, "독서") }
        runTest { assertThat(store().names().first()[PresetSlot.P4]).isEqualTo("독서") }
    }
}
