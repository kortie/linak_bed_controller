package dev.linakbed.domain.fake

import dev.linakbed.domain.port.PresetNameStore
import dev.linakbed.domain.preset.PresetName
import dev.linakbed.protocol.PresetSlot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakePresetNameStore(
    initial: Map<PresetSlot, String> = PresetSlot.entries.associateWith(PresetName::defaultFor),
) : PresetNameStore {
    val current = MutableStateFlow(initial)

    override fun names(): Flow<Map<PresetSlot, String>> = current

    override suspend fun rename(
        slot: PresetSlot,
        name: String,
    ) {
        current.value = current.value + (slot to name)
    }
}
