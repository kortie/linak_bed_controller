package dev.linakbed.ble

import dev.linakbed.domain.BedResult
import dev.linakbed.domain.port.BedLink
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Protocol Lab(디버그) 전용 기능. 포트([BedLink])에 BLE 개념을 넣지 않기 위해 분리한다 (ADR-006).
 * 모터를 움직이는 정식 경로는 BedController뿐이며, 이 인터페이스는 실기기 프로토콜 실측에만 쓴다.
 */
interface DiagnosticBedLink : BedLink {
    /** 마지막 연결에서 발견한 GATT 서비스·특성 목록. */
    val services: List<GattServiceInfo>

    /** 인코더를 거치지 않고 원시 바이트를 제어 특성에 쓴다. */
    suspend fun writeRaw(bytes: ByteArray): BedResult<Unit>
}

data class BleConfig(
    /** 연결 이후 각 GATT 작업(탐색·쓰기·읽기·notify)의 시간 제한. */
    val operationTimeout: Duration = 5.seconds,
    /** 센서가 notify를 지원하지 않을 때 읽기 주기. */
    val sensorPollInterval: Duration = 500.milliseconds,
)
