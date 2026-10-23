package dev.linakbed.app.log

/** 크래시를 동기적으로 기록할 수 있는 대상. */
fun interface CrashRecorder {
    fun recordCrash(
        thread: Thread,
        error: Throwable,
    )
}

/** 기본 UncaughtExceptionHandler 앞에 끼어들어 크래시를 기록한 뒤, 원래 핸들러에 그대로 넘긴다 (ADR-007). */
class CrashLogger(
    private val recorder: CrashRecorder,
) {
    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                recorder.recordCrash(thread, error)
            } catch (
                @Suppress("TooGenericExceptionCaught", "SwallowedException") recordFailure: Throwable,
            ) {
                // 기록 실패가 원래 크래시 처리를 막으면 안 된다.
            } finally {
                previous?.uncaughtException(thread, error)
            }
        }
    }
}
