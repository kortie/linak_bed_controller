package dev.linakbed.domain.fake

import dev.linakbed.domain.model.PairedDevice
import dev.linakbed.domain.port.PairingStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakePairingStore(
    device: PairedDevice? = DEFAULT,
) : PairingStore {
    val beds = MutableStateFlow(listOfNotNull(device))

    /** 첫 침대 (침대 한 대만 쓰는 테스트용). 바꾸면 목록이 그 한 대로 바뀐다. */
    var device: PairedDevice?
        get() = beds.value.firstOrNull()
        set(value) {
            beds.value = listOfNotNull(value)
        }

    override fun observeAll(): Flow<List<PairedDevice>> = beds

    override suspend fun save(device: PairedDevice): Boolean {
        val merged = PairingStore.merged(beds.value, device) ?: return false
        beds.value = merged
        return true
    }

    override suspend fun remove(bedId: String) {
        beds.value = beds.value.filterNot { it.bedId == bedId }
    }

    companion object {
        val DEFAULT = PairedDevice(bedId = "bed-1", address = "AA:BB:CC:DD:EE:FF", name = "Linak", cdmAssociationId = 1)
        val SECOND = PairedDevice(bedId = "bed-2", address = "11:22:33:44:55:66", name = "Linak 2", cdmAssociationId = 2)
    }
}
