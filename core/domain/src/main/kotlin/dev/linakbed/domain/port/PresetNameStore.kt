package dev.linakbed.domain.port

import dev.linakbed.protocol.PresetSlot
import kotlinx.coroutines.flow.Flow

/**
 * 프리셋 이름의 단일 출처 (위치 자체는 침대 하드웨어가 저장한다).
 * 원격 명령의 이름 매칭(S06)도 이 값을 쓰므로, 저장 전에 `PresetName.validate()`를 통과시킨다.
 */
interface PresetNameStore {
    /** 항상 4개 슬롯 모두 (저장된 이름이 없으면 기본 이름). */
    fun names(): Flow<Map<PresetSlot, String>>

    suspend fun rename(
        slot: PresetSlot,
        name: String,
    )
}
