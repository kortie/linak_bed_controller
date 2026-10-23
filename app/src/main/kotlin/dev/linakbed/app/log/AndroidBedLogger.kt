package dev.linakbed.app.log

import android.util.Log
import dev.linakbed.domain.log.BedLogger
import dev.linakbed.domain.log.LogLevel
import dev.linakbed.domain.port.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * [BedLogger] 구현: Logcat(`LinakBed/<Area>`) + [LogSink](파일)에 동시 기록 (ADR-007).
 *
 * 파일 쓰기는 [scope](단일 스레드)에서 비동기로 하고, WARN 이상은 즉시, 나머지는 [flushInterval]마다 flush한다.
 * `android.util.Log`를 직접 호출하는 유일한 클래스다 (linak-conventions §7).
 */
class AndroidBedLogger(
    private val sink: LogSink,
    private val minLevel: LogLevel,
    private val clock: Clock,
    scope: CoroutineScope,
    flushInterval: Duration = DEFAULT_FLUSH_INTERVAL,
) : BedLogger,
    CrashRecorder {
    private sealed interface Entry {
        data class Line(
            val text: String,
            val level: LogLevel,
        ) : Entry

        data object FlushTick : Entry
    }

    private val queue = Channel<Entry>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (entry in queue) {
                when (entry) {
                    is Entry.Line -> {
                        sink.write(entry.text)
                        if (entry.level >= LogLevel.WARN) sink.flush()
                    }
                    Entry.FlushTick -> sink.flush()
                }
            }
        }
        scope.launch {
            while (isActive) {
                delay(flushInterval)
                queue.send(Entry.FlushTick)
            }
        }
    }

    override fun log(
        level: LogLevel,
        area: String,
        message: String,
        error: Throwable?,
    ) {
        if (level < minLevel) return
        writeLogcat(level, area, message, error)
        queue.trySend(Entry.Line(format(level, area, message, error), level))
    }

    /** 크래시는 큐를 거치지 않고 호출 스레드에서 바로 쓰고 flush한다 (프로세스가 곧 종료되므로). */
    override fun recordCrash(
        thread: Thread,
        error: Throwable,
    ) {
        val message = "Uncaught exception in thread ${thread.name}"
        writeLogcat(LogLevel.ERROR, CRASH_AREA, message, error)
        sink.write(format(LogLevel.ERROR, CRASH_AREA, message, error))
        sink.flush()
    }

    private fun format(
        level: LogLevel,
        area: String,
        message: String,
        error: Throwable?,
    ) = LogLineFormatter.format(LogRecord(clock.now(), level, area, message, error), clock.zone())

    private fun writeLogcat(
        level: LogLevel,
        area: String,
        message: String,
        error: Throwable?,
    ) {
        val text = if (error == null) message else message + "\n" + Log.getStackTraceString(error)
        Log.println(level.toPriority(), TAG_PREFIX + area, text)
    }

    private fun LogLevel.toPriority(): Int =
        when (this) {
            LogLevel.DEBUG -> Log.DEBUG
            LogLevel.INFO -> Log.INFO
            LogLevel.WARN -> Log.WARN
            LogLevel.ERROR -> Log.ERROR
        }

    companion object {
        const val TAG_PREFIX = "LinakBed/"
        const val CRASH_AREA = "App"
        val DEFAULT_FLUSH_INTERVAL = 2.seconds
    }
}
