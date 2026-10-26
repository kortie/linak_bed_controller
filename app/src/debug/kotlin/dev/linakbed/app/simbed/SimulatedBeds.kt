package dev.linakbed.app.simbed

import dev.linakbed.domain.log.BedLogger
import dev.linakbed.domain.model.PairedDevice
import dev.linakbed.protocol.ProtocolProfile
import kotlinx.coroutines.CoroutineScope

/** 디버그 가상 침대들 (S13: 최대 2대). 주소마다 링크 하나를 만들어 두고 같은 주소에는 같은 링크를 준다. */
class SimulatedBeds(
    private val scope: CoroutineScope,
    private val logger: BedLogger,
) {
    private val links = mutableMapOf<String, SimulatedBedLink>()

    @Synchronized
    fun link(address: String): SimulatedBedLink = links.getOrPut(address) { SimulatedBedLink(ProtocolProfile.DEFAULT, scope, logger) }

    companion object {
        const val COUNT = 2

        /** 가상 침대 [index]번(1부터)의 페어링 정보. */
        fun device(index: Int) =
            PairedDevice(
                bedId = "simulated-bed-$index",
                address = SimulatedBedLink.addressOf(index),
                name = "가상 침대 $index",
                cdmAssociationId = -1,
            )

        fun isSimulated(address: String) = address.startsWith(SimulatedBedLink.ADDRESS_PREFIX)
    }
}
