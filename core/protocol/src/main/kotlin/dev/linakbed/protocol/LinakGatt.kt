package dev.linakbed.protocol

import java.util.UUID

/** Linak 컨트롤러 GATT UUID (README 기준, 미검증 — docs/spec/hw-findings.md). */
object LinakGatt {
    val SERVICE_CONTROL: UUID = UUID.fromString("99fa0001-338a-1024-8a49-009c0215f78a")
    val CHAR_CONTROL: UUID = UUID.fromString("99fa0002-338a-1024-8a49-009c0215f78a")
    val SERVICE_SENSOR: UUID = UUID.fromString("99fa0020-338a-1024-8a49-009c0215f78a")
    val CHAR_HEAD: UUID = UUID.fromString("99fa0028-338a-1024-8a49-009c0215f78a")
    val CHAR_FEET: UUID = UUID.fromString("99fa0027-338a-1024-8a49-009c0215f78a")

    /** BLE 표준 Client Characteristic Configuration 디스크립터 (notify 켜기). Linak 고유 값이 아님. */
    val CLIENT_CHARACTERISTIC_CONFIG: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
}
