package dev.linakbed.app.log

import com.google.common.truth.Truth.assertThat
import dev.linakbed.domain.fake.FakeClock
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

class FileLogSinkTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val clock = FakeClock()

    private fun sink(
        directory: File = File(temp.root, "logs"),
        maxFileBytes: Long = 1024,
        maxFiles: Int = 5,
    ) = FileLogSink(directory, clock, maxFileBytes = maxFileBytes, maxFiles = maxFiles)

    @Test
    fun `creates directory lazily and appends lines with newline`() {
        val directory = File(temp.root, "logs")
        val sink = sink(directory)
        assertThat(directory.exists()).isFalse()

        sink.write("first")
        sink.write("second")
        sink.flush()

        assertThat(File(directory, "bed-0.log").readText()).isEqualTo("first\nsecond\n")
    }

    @Test
    fun `appends to an existing current file`() {
        val directory = File(temp.root, "logs").apply { mkdirs() }
        File(directory, "bed-0.log").writeText("old\n")

        sink(directory).apply {
            write("new")
            flush()
        }

        assertThat(File(directory, "bed-0.log").readText()).isEqualTo("old\nnew\n")
    }

    @Test
    fun `rotates before the current file would exceed the size limit`() {
        val directory = File(temp.root, "logs")
        val sink = sink(directory, maxFileBytes = 10)

        sink.write("aaaa") // 5 bytes
        sink.write("bbbb") // 10 bytes total — still fits
        sink.write("cccc") // would be 15 → rotate first
        sink.flush()

        assertThat(File(directory, "bed-1.log").readText()).isEqualTo("aaaa\nbbbb\n")
        assertThat(File(directory, "bed-0.log").readText()).isEqualTo("cccc\n")
    }

    @Test
    fun `keeps at most maxFiles files, dropping the oldest`() {
        val directory = File(temp.root, "logs")
        val sink = sink(directory, maxFileBytes = 5, maxFiles = 3)

        listOf("1111", "2222", "3333", "4444", "5555").forEach(sink::write)
        sink.flush()

        assertThat(sink.files().map { it.name }).containsExactly("bed-0.log", "bed-1.log", "bed-2.log").inOrder()
        assertThat(sink.files().map { it.readText() }).containsExactly("5555\n", "4444\n", "3333\n").inOrder()
    }

    @Test
    fun `deletes log files older than retention when first opened`() {
        val directory = File(temp.root, "logs").apply { mkdirs() }
        val now = clock.now().toEpochMilli()
        val expired =
            File(directory, "bed-3.log").apply {
                writeText("old")
                setLastModified(now - 7.days.inWholeMilliseconds - 1.hours.inWholeMilliseconds)
            }
        val recent =
            File(directory, "bed-2.log").apply {
                writeText("recent")
                setLastModified(now - 6.days.inWholeMilliseconds)
            }
        val unrelated =
            File(directory, "notes.txt").apply {
                writeText("keep")
                setLastModified(0)
            }

        sink(directory).write("line")

        assertThat(expired.exists()).isFalse()
        assertThat(recent.exists()).isTrue()
        assertThat(unrelated.exists()).isTrue()
    }

    @Test
    fun `close flushes and allows reopening`() {
        val directory = File(temp.root, "logs")
        val sink = sink(directory)

        sink.write("before close")
        sink.close()
        sink.write("after close")
        sink.close()

        assertThat(File(directory, "bed-0.log").readText()).isEqualTo("before close\nafter close\n")
    }
}
