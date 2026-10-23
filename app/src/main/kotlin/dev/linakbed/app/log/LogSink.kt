package dev.linakbed.app.log

/** 포맷된 로그 줄을 받는 출력 대상. 구현은 스레드 안전해야 한다 (크래시 경로가 다른 스레드에서 직접 쓴다). */
interface LogSink {
    fun write(line: String)

    fun flush()
}
