package dev.linakbed.domain.log

/**
 * 모든 모듈의 로그 진입점 (ADR-007). 구현은 `app`의 `AndroidBedLogger`(Logcat + 파일).
 *
 * [area]는 짧은 고정 이름(`Ble`, `Controller`, `Safety`, `Remote`, `Notification`, `Service`, `Ui`, `App`)을 쓴다.
 * 메시지에 MAC 주소·PIN을 넣을 때는 반드시 [LogRedaction]을 거친다.
 */
interface BedLogger {
    fun log(
        level: LogLevel,
        area: String,
        message: String,
        error: Throwable? = null,
    )
}

fun BedLogger.debug(
    area: String,
    message: String,
) = log(LogLevel.DEBUG, area, message)

fun BedLogger.info(
    area: String,
    message: String,
) = log(LogLevel.INFO, area, message)

fun BedLogger.warn(
    area: String,
    message: String,
    error: Throwable? = null,
) = log(LogLevel.WARN, area, message, error)

fun BedLogger.error(
    area: String,
    message: String,
    error: Throwable? = null,
) = log(LogLevel.ERROR, area, message, error)
