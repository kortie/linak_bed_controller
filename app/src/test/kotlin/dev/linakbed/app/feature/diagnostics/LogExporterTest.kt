package dev.linakbed.app.feature.diagnostics

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.linakbed.domain.fake.FakeClock
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipInputStream

/** ADR-007: 로그 파일 전부를 zip으로, FileProvider URI로 공유한다. */
@RunWith(AndroidJUnit4::class)
class LogExporterTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val logs = File(app.filesDir, "test-logs").apply { mkdirs() }
    private val exporter = LogExporter(app, logs, FakeClock())

    @Before
    fun clearProviderCache() = resetFileProviderCache()

    @Test
    fun `no log files - no share intent`() {
        logs.listFiles()?.forEach { it.delete() }
        File(logs, "notes.txt").writeText("not a log")

        assertThat(exporter.shareIntent()).isNull()
    }

    @Test
    fun `all log files are zipped and shared through the FileProvider`() {
        File(logs, "linakbed-1.log").writeText("first")
        File(logs, "linakbed-0.log").writeText("second")

        val intent = exporter.shareIntent()!!

        assertThat(intent.action).isEqualTo(Intent.ACTION_SEND)
        assertThat(intent.type).isEqualTo("application/zip")
        assertThat(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION).isNotEqualTo(0)
        @Suppress("DEPRECATION")
        val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!
        assertThat(uri.authority).isEqualTo("${app.packageName}.logs")
        assertThat(uri.lastPathSegment).isEqualTo("linakbed-logs-20261023-160000.zip")

        val zip = File(File(app.cacheDir, LogExporter.EXPORT_DIR), uri.lastPathSegment!!)
        val entries = mutableMapOf<String, String>()
        ZipInputStream(zip.inputStream()).use { input ->
            generateSequence { input.nextEntry }.forEach { entries[it.name] = input.readBytes().decodeToString() }
        }
        assertThat(entries).containsExactly("linakbed-0.log", "second", "linakbed-1.log", "first")
    }
}
