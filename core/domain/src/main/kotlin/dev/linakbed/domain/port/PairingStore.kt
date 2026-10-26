package dev.linakbed.domain.port

import dev.linakbed.domain.model.PairedDevice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/** 연결(페어링)한 침대 목록 (ADR-012, 최대 [MAX_BEDS]대). */
interface PairingStore {
    /** 침대 목록이 바뀔 때마다 현재 목록을 낸다 (연결 안내 ↔ 홈, 카드 수). */
    fun observeAll(): Flow<List<PairedDevice>>

    suspend fun all(): List<PairedDevice> = observeAll().first()

    /** 같은 `bedId` 또는 같은 주소의 침대가 있으면 교체, 없으면 추가. 이미 [MAX_BEDS]대면 추가하지 않고 false. */
    suspend fun save(device: PairedDevice): Boolean

    suspend fun remove(bedId: String)

    companion object {
        const val MAX_BEDS = 2

        /** [save] 규칙을 목록에 적용한 결과 (저장소 구현 공용). 추가할 수 없으면 null. */
        fun merged(
            current: List<PairedDevice>,
            device: PairedDevice,
        ): List<PairedDevice>? {
            val index = current.indexOfFirst { it.bedId == device.bedId || it.address.equals(device.address, ignoreCase = true) }
            return when {
                index >= 0 -> current.toMutableList().also { it[index] = device }
                current.size < MAX_BEDS -> current + device
                else -> null
            }
        }
    }
}

/** 침대 한 대의 컨트롤러가 연결할 대상 (ADR-012). 페어링이 해제됐으면 null. */
fun interface PairedBedSource {
    suspend fun current(): PairedDevice?
}
