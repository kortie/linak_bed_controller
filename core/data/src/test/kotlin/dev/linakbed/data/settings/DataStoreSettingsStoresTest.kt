package dev.linakbed.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.google.common.truth.Truth.assertThat
import dev.linakbed.domain.remote.RemoteSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.time.Duration.Companion.seconds

class DataStoreSettingsStoresTest {
    @get:Rule
    val temp = TemporaryFolder()

    private fun TestScope.remote() =
        DataStoreRemoteSettingsStore(PreferenceDataStoreFactory.create(scope = backgroundScope) { File(temp.root, "r.preferences_pb") })

    @Test
    fun `remote settings default to spec defaults`() =
        runTest {
            val stored = remote().settings().first()
            assertThat(stored).isEqualTo(RemoteSettings())
            assertThat(stored.appShortcutsEnabled).isTrue()
        }

    @Test
    fun `remote settings round-trip every field`() =
        runTest {
            val store = remote()
            val changed =
                RemoteSettings(
                    appShortcutsEnabled = false,
                    freshness = 90.seconds,
                    rateLimitPerMinute = 3,
                    preExecutionDelay = 20.seconds,
                    notifyOnSuccess = true,
                )

            store.update { changed }

            assertThat(store.settings().first()).isEqualTo(changed)
        }
}
