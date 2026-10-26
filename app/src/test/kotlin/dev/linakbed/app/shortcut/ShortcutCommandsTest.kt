package dev.linakbed.app.shortcut

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.linakbed.domain.remote.RemoteCommand
import dev.linakbed.protocol.LightAction
import dev.linakbed.protocol.PresetSlot
import org.junit.Test
import org.junit.runner.RunWith

/** 02 문서 Part C1. */
@RunWith(AndroidJUnit4::class)
class ShortcutCommandsTest {
    private fun parse(text: String?) = ShortcutCommands.parse(text?.let(Uri::parse))

    @Test
    fun `preset 1 to 4 and stop are understood`() {
        PresetSlot.entries.forEach { slot ->
            assertThat(parse("bedcontrol://shortcut/preset/${slot.number}")).isEqualTo(RemoteCommand.Preset(slot))
        }
        assertThat(parse("bedcontrol://shortcut/stop")).isEqualTo(RemoteCommand.Stop)
    }

    @Test
    fun `anything else is not a command`() {
        listOf(
            null,
            "",
            "bedcontrol://shortcut/preset/0",
            "bedcontrol://shortcut/preset/5",
            "bedcontrol://shortcut/preset/two",
            "bedcontrol://shortcut/preset",
            "bedcontrol://shortcut/preset/1/2",
            "bedcontrol://other/stop",
            "https://shortcut/stop",
            "bedcontrol://shortcut/light",
        ).forEach { assertThat(parse(it)).isNull() }
    }

    @Test
    fun `uriOf round-trips and light has no shortcut`() {
        (PresetSlot.entries.map(RemoteCommand::Preset) + RemoteCommand.Stop).forEach { command ->
            assertThat(parse(ShortcutCommands.uriOf(command))).isEqualTo(command)
        }
        assertThat(ShortcutCommands.uriOf(RemoteCommand.Light(LightAction.ON))).isNull()
    }
}
