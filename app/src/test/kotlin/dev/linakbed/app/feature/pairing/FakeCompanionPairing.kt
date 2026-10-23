package dev.linakbed.app.feature.pairing

import dev.linakbed.app.pairing.AssociationEvent
import dev.linakbed.app.pairing.CompanionPairing
import dev.linakbed.app.pairing.PairingFilter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

class FakeCompanionPairing : CompanionPairing {
    val events = MutableSharedFlow<AssociationEvent>(extraBufferCapacity = 8)
    val requestedFilters = mutableListOf<PairingFilter>()
    val disassociated = mutableListOf<Int>()

    override fun associate(filter: PairingFilter): Flow<AssociationEvent> {
        requestedFilters += filter
        return events
    }

    override fun disassociate(associationId: Int) {
        disassociated += associationId
    }
}
