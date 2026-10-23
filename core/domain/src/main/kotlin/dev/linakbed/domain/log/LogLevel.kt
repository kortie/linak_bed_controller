package dev.linakbed.domain.log

/** 로그 레벨. [symbol]은 로그 파일 한 줄의 레벨 표기(`I/Remote: ...`). */
enum class LogLevel(
    val symbol: Char,
) {
    DEBUG('D'),
    INFO('I'),
    WARN('W'),
    ERROR('E'),
}
