package dev.linakbed.app.feature.about

import android.app.Activity
import kotlinx.coroutines.flow.MutableSharedFlow

/** Play 없이 후원 흐름을 시험하는 [TipBilling]. */
class FakeTipBilling(
    var available: Boolean = true,
    var productPrice: String? = "₩1,500",
    var leftovers: List<TipPurchase> = emptyList(),
    var launchSucceeds: Boolean = true,
    var consumeSucceeds: Boolean = true,
) : TipBilling {
    override val results = MutableSharedFlow<TipResult>(extraBufferCapacity = 8)
    val launched = mutableListOf<String>()
    val consumed = mutableListOf<String>()

    override suspend fun connect() = available

    override suspend fun price(productId: String) = productPrice

    override fun launch(
        activity: Activity,
        productId: String,
    ): Boolean {
        launched += productId
        return launchSucceeds
    }

    override suspend fun unconsumed(productId: String) = leftovers

    override suspend fun consume(purchase: TipPurchase): Boolean {
        consumed += purchase.token
        if (consumeSucceeds) leftovers = leftovers - purchase
        return consumeSucceeds
    }
}
