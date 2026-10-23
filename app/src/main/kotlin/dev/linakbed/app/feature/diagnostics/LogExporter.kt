package dev.linakbed.app.feature.diagnostics

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import dev.linakbed.domain.port.Clock
import java.io.File
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** 로그 파일을 zip으로 묶어 공유 시트로 내보낸다 (ADR-007). 앱이 외부로 자동 전송하지 않는다. */
class LogExporter(
    private val context: Context,
    val logDirectory: File,
    private val clock: Clock,
) {
    /** 로그가 없으면 null. */
    fun shareIntent(): Intent? {
        val logs = logDirectory.listFiles { file -> file.isFile && file.name.endsWith(".log") }?.sortedBy { it.name }.orEmpty()
        if (logs.isEmpty()) return null
        val exports = File(context.cacheDir, EXPORT_DIR).apply { mkdirs() }
        exports.listFiles()?.forEach { it.delete() }
        val stamp = STAMP.format(clock.now().atZone(clock.zone()))
        val zip = File(exports, "linakbed-logs-$stamp.zip")
        ZipOutputStream(zip.outputStream()).use { out ->
            logs.forEach { log ->
                out.putNextEntry(ZipEntry(log.name))
                log.inputStream().use { it.copyTo(out) }
                out.closeEntry()
            }
        }
        val uri = FileProvider.getUriForFile(context, authority(context), zip)
        return Intent(Intent.ACTION_SEND)
            .setType("application/zip")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    companion object {
        const val EXPORT_DIR = "exports"
        private val STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

        fun authority(context: Context) = "${context.packageName}.logs"
    }
}
