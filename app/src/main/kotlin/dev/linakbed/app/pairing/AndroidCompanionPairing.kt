package dev.linakbed.app.pairing

import android.bluetooth.le.ScanFilter
import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.BluetoothLeDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.IntentSender
import android.os.ParcelUuid
import dev.linakbed.protocol.LinakGatt
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.regex.Pattern

class AndroidCompanionPairing(
    private val context: Context,
) : CompanionPairing {
    private val manager: CompanionDeviceManager get() = context.getSystemService(CompanionDeviceManager::class.java)

    override fun associate(filter: PairingFilter): Flow<AssociationEvent> =
        callbackFlow {
            val request =
                AssociationRequest
                    .Builder()
                    .addDeviceFilter(filter.toDeviceFilter())
                    .setSingleDevice(false)
                    .build()
            manager.associate(
                request,
                context.mainExecutor,
                object : CompanionDeviceManager.Callback() {
                    override fun onAssociationPending(intentSender: IntentSender) {
                        trySend(AssociationEvent.Pending(intentSender))
                    }

                    override fun onAssociationCreated(associationInfo: AssociationInfo) {
                        val address = associationInfo.deviceMacAddress?.toString()?.uppercase()
                        trySend(
                            if (address == null) {
                                AssociationEvent.Failed("no MAC address")
                            } else {
                                AssociationEvent.Created(associationInfo.id, address, associationInfo.displayName?.toString())
                            },
                        )
                        close()
                    }

                    override fun onFailure(error: CharSequence?) {
                        trySend(AssociationEvent.Failed(error?.toString()))
                        close()
                    }
                },
            )
            awaitClose()
        }

    override fun disassociate(associationId: Int) {
        manager.disassociate(associationId)
    }

    private fun PairingFilter.toDeviceFilter(): BluetoothLeDeviceFilter {
        val builder = BluetoothLeDeviceFilter.Builder()
        when (this) {
            PairingFilter.ControlServiceUuid ->
                builder.setScanFilter(ScanFilter.Builder().setServiceUuid(ParcelUuid(LinakGatt.SERVICE_CONTROL)).build())
            is PairingFilter.NamePattern -> builder.setNamePattern(Pattern.compile(regex))
        }
        return builder.build()
    }
}
