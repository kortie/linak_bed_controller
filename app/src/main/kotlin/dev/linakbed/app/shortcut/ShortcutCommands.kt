package dev.linakbed.app.shortcut

import android.net.Uri
import dev.linakbed.domain.remote.RemoteCommand
import dev.linakbed.protocol.PresetSlot

/**
 * 앱 바로가기 URI ↔ 원격 명령 (02 문서 Part C1, ADR-011). `res/xml/shortcuts.xml`과 같은 값.
 * 바로가기 ID·URI는 사용자가 만든 루틴이 참조하므로 배포 후 바꾸지 않는다.
 */
object ShortcutCommands {
    const val SCHEME = "bedcontrol"
    const val HOST = "shortcut"
    private const val PRESET = "preset"
    private const val STOP = "stop"

    fun parse(uri: Uri?): RemoteCommand? {
        if (uri == null || uri.scheme != SCHEME || uri.host != HOST) return null
        val segments = uri.pathSegments
        return when {
            segments == listOf(STOP) -> RemoteCommand.Stop
            segments.size == 2 && segments[0] == PRESET ->
                segments[1].toIntOrNull()?.let(PresetSlot::fromNumber)?.let(RemoteCommand::Preset)
            else -> null
        }
    }

    fun uriOf(command: RemoteCommand): String? =
        when (command) {
            is RemoteCommand.Preset -> "$SCHEME://$HOST/$PRESET/${command.slot.number}"
            RemoteCommand.Stop -> "$SCHEME://$HOST/$STOP"
            is RemoteCommand.Light -> null
        }
}
