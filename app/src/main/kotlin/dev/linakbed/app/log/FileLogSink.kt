package dev.linakbed.app.log

import dev.linakbed.domain.port.Clock
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/**
 * 순환 로그 파일 (ADR-007). `bed-0.log`가 현재 파일이고, [maxFileBytes]를 넘기 전에
 * `bed-0 → bed-1 → …`으로 밀어내며 최대 [maxFiles]개를 유지한다. 처음 쓸 때 [retention]보다 오래된 파일을 지운다.
 *
 * java.io만 쓰므로 JVM 단위 테스트가 가능하다. 디렉터리 생성·정리는 첫 [write]에서 하므로 생성자는 디스크를 건드리지 않는다.
 */
class FileLogSink(
    private val directory: File,
    private val clock: Clock,
    private val maxFileBytes: Long = DEFAULT_MAX_FILE_BYTES,
    private val maxFiles: Int = DEFAULT_MAX_FILES,
    private val retention: Duration = DEFAULT_RETENTION,
) : LogSink {
    private var output: OutputStream? = null
    private var currentSize = 0L

    /** 존재하는 로그 파일, 최신(`bed-0.log`)부터. */
    fun files(): List<File> = (0 until maxFiles).map { fileAt(it) }.filter { it.exists() }

    @Synchronized
    override fun write(line: String) {
        val bytes = (line + "\n").toByteArray(Charsets.UTF_8)
        val stream = output ?: open()
        val target =
            if (currentSize > 0 && currentSize + bytes.size > maxFileBytes) {
                rotate()
            } else {
                stream
            }
        target.write(bytes)
        currentSize += bytes.size
    }

    @Synchronized
    override fun flush() {
        output?.flush()
    }

    @Synchronized
    fun close() {
        output?.close()
        output = null
    }

    private fun open(): OutputStream {
        directory.mkdirs()
        deleteExpired()
        val file = fileAt(0)
        currentSize = file.length()
        return BufferedOutputStream(FileOutputStream(file, true)).also { output = it }
    }

    private fun rotate(): OutputStream {
        output?.close()
        fileAt(maxFiles - 1).delete()
        for (index in maxFiles - 2 downTo 0) {
            val file = fileAt(index)
            if (file.exists()) file.renameTo(fileAt(index + 1))
        }
        currentSize = 0
        return BufferedOutputStream(FileOutputStream(fileAt(0), false)).also { output = it }
    }

    private fun deleteExpired() {
        val cutoff = clock.now().toEpochMilli() - retention.inWholeMilliseconds
        directory
            .listFiles { file -> LOG_FILE_PATTERN.matches(file.name) }
            ?.filter { it.lastModified() < cutoff }
            ?.forEach { it.delete() }
    }

    private fun fileAt(index: Int) = File(directory, "bed-$index.log")

    companion object {
        const val DEFAULT_MAX_FILE_BYTES = 1L * 1024 * 1024
        const val DEFAULT_MAX_FILES = 5
        val DEFAULT_RETENTION = 7.days
        private val LOG_FILE_PATTERN = Regex("""bed-\d+\.log""")
    }
}
