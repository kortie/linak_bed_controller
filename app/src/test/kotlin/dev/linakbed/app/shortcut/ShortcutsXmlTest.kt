package dev.linakbed.app.shortcut

import android.app.Application
import android.content.ComponentName
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.linakbed.app.BuildConfig
import dev.linakbed.app.R
import dev.linakbed.domain.remote.RemoteCommand
import dev.linakbed.protocol.PresetSlot
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser

/** 정적 바로가기가 앱과 어긋나지 않는지 (ADR-011): 정적 XML은 ${applicationId}를 못 써서 직접 확인한다. */
@RunWith(AndroidJUnit4::class)
class ShortcutsXmlTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()

    private data class Shortcut(
        val id: String,
        val data: String?,
        val targetPackage: String?,
        val targetClass: String?,
    )

    private fun shortcuts(): List<Shortcut> {
        val parser = app.resources.getXml(R.xml.shortcuts)
        val result = mutableListOf<Shortcut>()
        var id: String? = null
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.name) {
                "shortcut" -> id = parser.getAttributeValue(ANDROID, "shortcutId")
                "intent" ->
                    result +=
                        Shortcut(
                            id = checkNotNull(id),
                            data = parser.getAttributeValue(ANDROID, "data"),
                            targetPackage = parser.getAttributeValue(ANDROID, "targetPackage"),
                            targetClass = parser.getAttributeValue(ANDROID, "targetClass"),
                        )
            }
        }
        return result
    }

    @Test
    fun `five shortcuts with stable ids and URIs that parse to the right command`() {
        val expected = PresetSlot.entries.associate { "preset_${it.number}" to RemoteCommand.Preset(it) } + ("stop" to RemoteCommand.Stop)

        val actual = shortcuts()

        assertThat(actual.map { it.id }).containsExactlyElementsIn(expected.keys).inOrder()
        actual.forEach { shortcut ->
            assertThat(shortcut.data).isEqualTo(ShortcutCommands.uriOf(expected.getValue(shortcut.id)))
        }
    }

    @Test
    fun `every shortcut targets this app's relay activity`() {
        shortcuts().forEach { shortcut ->
            assertThat(shortcut.targetPackage).isEqualTo(BuildConfig.APPLICATION_ID)
            assertThat(shortcut.targetClass).isEqualTo(ShortcutActivity::class.java.name)
        }
    }

    @Test
    fun `the relay activity is not exported`() {
        @Suppress("DEPRECATION") // Robolectric은 ComponentInfoFlags 판을 지원하지 않는다
        val info = app.packageManager.getActivityInfo(ComponentName(app, ShortcutActivity::class.java), 0)

        assertThat(info.exported).isFalse()
    }

    private companion object {
        const val ANDROID = "http://schemas.android.com/apk/res/android"
    }
}
