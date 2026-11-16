package dev.linakbed.app.feature.about

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.linakbed.domain.log.BedLogger
import dev.linakbed.domain.log.info
import dev.linakbed.domain.log.warn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** [TipBilling]의 Google Play Billing Library 구현 (ADR-014). 앱 전체에서 연결 하나를 쓴다. */
@Singleton
class PlayTipBilling
    @Inject
    constructor(
        @ApplicationContext context: Context,
        private val logger: BedLogger,
    ) : TipBilling {
        private val updates = MutableSharedFlow<TipResult>(extraBufferCapacity = RESULT_BUFFER)
        override val results: Flow<TipResult> = updates

        private val client: BillingClient =
            BillingClient
                .newBuilder(context)
                .setListener { result, purchases -> updates.tryEmit(result.toTipResult(purchases)) }
                .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
                .enableAutoServiceReconnection()
                .build()

        private val products = ConcurrentHashMap<String, ProductDetails>()
        private val connecting = Mutex()

        override suspend fun connect(): Boolean =
            connecting.withLock {
                client.isReady || startConnection()
            }

        private suspend fun startConnection(): Boolean =
            suspendCancellableCoroutine { cont ->
                client.startConnection(
                    object : BillingClientStateListener {
                        override fun onBillingSetupFinished(result: BillingResult) {
                            if (result.responseCode !=
                                BillingResponseCode.OK
                            ) {
                                logger.info(AREA, "billing unavailable: ${result.describe()}")
                            }
                            if (cont.isActive) cont.resume(result.responseCode == BillingResponseCode.OK)
                        }

                        override fun onBillingServiceDisconnected() {
                            if (cont.isActive) cont.resume(false)
                        }
                    },
                )
            }

        override suspend fun price(productId: String): String? {
            val params =
                QueryProductDetailsParams
                    .newBuilder()
                    .setProductList(
                        listOf(
                            QueryProductDetailsParams.Product
                                .newBuilder()
                                .setProductId(productId)
                                .setProductType(ProductType.INAPP)
                                .build(),
                        ),
                    ).build()
            val product =
                suspendCancellableCoroutine { cont ->
                    client.queryProductDetailsAsync(params) { result, details ->
                        val found = details.productDetailsList.firstOrNull().takeIf { result.responseCode == BillingResponseCode.OK }
                        if (found == null) logger.info(AREA, "product $productId not available: ${result.describe()}")
                        cont.resume(found)
                    }
                }
            product?.let { products[productId] = it }
            return product?.oneTimePurchaseOfferDetails?.formattedPrice
        }

        override fun launch(
            activity: Activity,
            productId: String,
        ): Boolean {
            val product = products[productId] ?: return false
            val item =
                BillingFlowParams.ProductDetailsParams
                    .newBuilder()
                    .setProductDetails(product)
                    .apply { product.oneTimePurchaseOfferDetails?.offerToken?.let(::setOfferToken) }
                    .build()
            val result =
                client.launchBillingFlow(
                    activity,
                    BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(item)).build(),
                )
            if (result.responseCode != BillingResponseCode.OK) logger.warn(AREA, "billing flow not shown: ${result.describe()}")
            return result.responseCode == BillingResponseCode.OK
        }

        override suspend fun unconsumed(productId: String): List<TipPurchase> =
            suspendCancellableCoroutine { cont ->
                client.queryPurchasesAsync(
                    QueryPurchasesParams.newBuilder().setProductType(ProductType.INAPP).build(),
                ) { result, purchases ->
                    val ours = purchases.filter { productId in it.products }.mapNotNull { it.toTip() }
                    cont.resume(if (result.responseCode == BillingResponseCode.OK) ours else emptyList())
                }
            }

        override suspend fun consume(purchase: TipPurchase): Boolean =
            suspendCancellableCoroutine { cont ->
                client.consumeAsync(ConsumeParams.newBuilder().setPurchaseToken(purchase.token).build()) { result, _ ->
                    if (result.responseCode != BillingResponseCode.OK) logger.warn(AREA, "consume failed: ${result.describe()}")
                    cont.resume(result.responseCode == BillingResponseCode.OK)
                }
            }

        private fun BillingResult.toTipResult(purchases: List<Purchase>?): TipResult =
            when (responseCode) {
                BillingResponseCode.OK -> TipResult.Purchased(purchases.orEmpty().mapNotNull { it.toTip() })
                BillingResponseCode.USER_CANCELED -> TipResult.Cancelled
                else -> TipResult.Failed(responseCode).also { logger.warn(AREA, "purchase failed: ${describe()}") }
            }

        private fun Purchase.toTip(): TipPurchase? =
            when (purchaseState) {
                Purchase.PurchaseState.PURCHASED -> TipPurchase(purchaseToken, TipPurchase.State.PURCHASED)
                Purchase.PurchaseState.PENDING -> TipPurchase(purchaseToken, TipPurchase.State.PENDING)
                else -> null
            }

        private fun BillingResult.describe() = "code=$responseCode $debugMessage"

        private companion object {
            const val AREA = "Billing"
            const val RESULT_BUFFER = 8
        }
    }
