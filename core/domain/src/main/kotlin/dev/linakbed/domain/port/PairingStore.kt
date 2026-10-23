package dev.linakbed.domain.port

import dev.linakbed.domain.model.PairedDevice
import kotlinx.coroutines.flow.Flow

interface PairingStore {
    suspend fun pairedDevice(): PairedDevice?

    /** 페어링이 바뀔 때마다 현재 값을 낸다 (연결 안내 ↔ 홈 전환, S09). */
    fun observe(): Flow<PairedDevice?>

    suspend fun save(device: PairedDevice?)
}
