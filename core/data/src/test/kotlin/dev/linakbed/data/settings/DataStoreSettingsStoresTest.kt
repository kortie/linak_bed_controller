package dev.linakbed.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.google.common.truth.Truth.assertThat
import dev.linakbed.domain.remote.RemoteSettings
import dev.linakbed.domain.remote.notification.NotificationChannelSettings
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

    private fun TestScope.channel() =
        DataStoreNotificationChannelSettingsStore(
            PreferenceDataStoreFactory.create(scope = backgroundScope) { File(temp.root, "c.preferences_pb") },
        )

    @Test
    fun `remote settings default to disabled with spec defaults`() =
        runTest {
            assertThat(remote().settings().first()).isEqualTo(RemoteSettings())
            assertThat(RemoteSettings().enabled).isFalse()
        }

    @Test
    fun `remote settings round-trip every field`() =
        runTest {
            val store = remote()
            val changed =
                RemoteSettings(
                    enabled = true,
                    freshness = 90.seconds,
                    rateLimitPerMinute = 3,
                    preExecutionDelay = 20.seconds,
                    notifyOnSuccess = true,
                )

            store.update { changed }

            assertThat(store.settings().first()).isEqualTo(changed)
        }

    @Test
    fun `channel settings default to the SmartThings package and no PIN`() =
        runTest {
            assertThat(channel().settings().first()).isEqualTo(NotificationChannelSettings())
        }

    @Test
    fun `channel settings round-trip and the PIN can be cleared`() =
        runTest {
            val store = channel()
            val changed =
                NotificationChannelSettings(
                    prefix = "!침대",
                    pin = "4821",
                    allowedPackages = setOf("a.b", "c.d"),
                    dismissHandledNotification = false,
                )

            store.update { changed }
            assertThat(store.settings().first()).isEqualTo(changed)

            store.update { it.copy(pin = null) }
            assertThat(store.settings().first().pin).isNull()
        }
}
