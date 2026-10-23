package dev.linakbed.app.log

class FakeLogSink : LogSink {
    val lines = mutableListOf<String>()
    var flushCount = 0
        private set

    /** flush 시점까지 쓰인 줄 수 — "언제 flush됐는가"를 검사한다. */
    val linesAtFlush = mutableListOf<Int>()

    override fun write(line: String) {
        lines += line
    }

    override fun flush() {
        flushCount++
        linesAtFlush += lines.size
    }
}
