package dev.linakbed.app.di

import android.content.Context
import dev.linakbed.ble.AndroidBedLink
import dev.linakbed.ble.AndroidBleEnvironment
import dev.linakbed.ble.AndroidGattConnector
import dev.linakbed.domain.log.BedLogger
import dev.linakbed.domain.model.PairedDevice
import dev.linakbed.domain.port.BedLink
import dev.linakbed.protocol.ProtocolProfile
import kotlinx.coroutines.CoroutineScope

/** 침대마다 연결을 하나씩 만든다 (ADR-012). release는 실제 BLE만, debug는 가상 침대 주소면 가상 링크. */
fun interface BedLinkFactory {
    fun create(device: PairedDevice): BedLink
}

/** 실제 BLE 링크 하나 (침대 하나당 GATT 연결 하나, A2). */
fun newAndroidBedLink(
    context: Context,
    logger: BedLogger,
    scope: CoroutineScope,
): AndroidBedLink =
    AndroidBedLink(
        connector = AndroidGattConnector(context),
        environment = AndroidBleEnvironment(context),
        profile = ProtocolProfile.DEFAULT,
        logger = logger,
        scope = scope,
    )
