package dev.linakbed.domain.fake

import dev.linakbed.domain.model.PairedDevice
import dev.linakbed.domain.port.PairingStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakePairingStore(
    device: PairedDevice? = DEFAULT,
) : PairingStore {
    private val current = MutableStateFlow(device)

    var device: PairedDevice?
        get() = current.value
        set(value) {
            current.value = value
        }

    override suspend fun pairedDevice(): PairedDevice? = device

    override fun observe(): Flow<PairedDevice?> = current

    override suspend fun save(device: PairedDevice?) {
        this.device = device
    }

    companion object {
        val DEFAULT = PairedDevice(bedId = "bed-1", address = "AA:BB:CC:DD:EE:FF", name = "Linak", cdmAssociationId = 1)
    }
}
