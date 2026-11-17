package dev.linakbed.domain.controller

import dev.linakbed.domain.log.BedLogger
import dev.linakbed.domain.log.info
import dev.linakbed.domain.model.PairedDevice
import dev.linakbed.domain.port.BedLink
import dev.linakbed.domain.port.PairedBedSource
import dev.linakbed.domain.port.PairingStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 침대 한 대를 이루는 부품 — 연결과 그 침대만의 컨트롤러 (ADR-012). */
class BedParts(
    val link: BedLink,
    val controller: BedController,
)

/** 침대 한 대: 페어링 정보(이름 등은 바뀔 수 있음) + 부품. */
data class BedUnit(
    val device: PairedDevice,
    val controller: BedController,
)

/** 침대마다 링크·컨트롤러를 만든다 (앱 DI가 구현: 실제 BLE 또는 디버그 가상 침대). */
fun interface BedPartsFactory {
    fun create(
        device: PairedDevice,
        source: PairedBedSource,
    ): BedParts
}

/**
 * 연결한 침대들 (ADR-012). 페어링 목록을 따라 침대마다 링크·컨트롤러를 만들고, 목록에서 빠진 침대는 연결을 끊고 버린다.
 * 원격 명령·전체 STOP은 [all]로 모든 침대에, 수동 조작은 [controller]로 침대 한 대에.
 */
class BedFleet(
    private val scope: CoroutineScope,
    private val store: PairingStore,
    private val factory: BedPartsFactory,
    private val logger: BedLogger,
) {
    private val entries = mutableMapOf<String, BedParts>()
    private val mutableBeds = MutableStateFlow<List<BedUnit>>(emptyList())

    /** 페어링 순서대로의 침대 목록. */
    val beds: StateFlow<List<BedUnit>> = mutableBeds.asStateFlow()

    /** 모든 침대에 같은 동작을 보내는 묶음 (원격 명령·전체 STOP). */
    val all: BedActions = FleetBedActions(beds, scope)

    init {
        scope.launch { store.observeAll().collect(::sync) }
    }

    fun controller(bedId: String): BedController? = beds.value.firstOrNull { it.device.bedId == bedId }?.controller

    private suspend fun sync(devices: List<PairedDevice>) {
        val ids = devices.map { it.bedId }.toSet()
        (entries.keys - ids).forEach { id ->
            val removed = entries.remove(id) ?: return@forEach
            removed.link.disconnect()
            logger.info(AREA, "bed removed")
        }
        devices.filter { it.bedId !in entries }.forEach { device -> entries[device.bedId] = assemble(device) }
        mutableBeds.value = devices.map { BedUnit(it, entries.getValue(it.bedId).controller) }
    }

    private fun assemble(device: PairedDevice): BedParts {
        val id = device.bedId
        val parts = factory.create(device) { store.all().firstOrNull { it.bedId == id } }
        logger.info(AREA, "bed added")
        return parts
    }

    private companion object {
        const val AREA = "Controller"
    }
}
