package dev.linakbed.app.feature.about

import android.app.Activity
import kotlinx.coroutines.flow.Flow

/** 후원 결제 한 건. [token]으로 소비한다 (ADR-014). */
data class TipPurchase(
    val token: String,
    val state: State,
) {
    enum class State { PURCHASED, PENDING }
}

/** 결제 창이 닫힌 뒤 Play가 알려 주는 결과. */
sealed interface TipResult {
    data class Purchased(
        val purchases: List<TipPurchase>,
    ) : TipResult

    data object Cancelled : TipResult

    data class Failed(
        val code: Int,
    ) : TipResult
}

/**
 * Google Play 결제와 앱 사이의 얇은 경계 (ADR-014). 실제 구현은 [PlayTipBilling], 테스트는 Fake.
 * 어떤 함수도 예외를 던지지 않는다 — Play를 쓸 수 없으면 false·null·빈 목록.
 */
interface TipBilling {
    /** 결제 창 결과. 앱이 살아 있는 동안 계속 흐른다. */
    val results: Flow<TipResult>

    /** Play 결제를 쓸 수 있으면 true. */
    suspend fun connect(): Boolean

    /** 상품의 현지화된 가격 (예: "₩1,500"). 상품이 없거나 Play에 올리지 않은 빌드면 null. */
    suspend fun price(productId: String): String?

    /** 결제 창을 띄운다. [price]로 상품을 먼저 읽어야 한다. 띄우지 못하면 false. */
    fun launch(
        activity: Activity,
        productId: String,
    ): Boolean

    /** 아직 소비하지 않은 구매 (결제 후 소비 전에 앱이 꺼진 경우 등). */
    suspend fun unconsumed(productId: String): List<TipPurchase>

    /** 소비한다 — 다시 후원할 수 있게 되고, Play의 3일 내 확인 규칙도 채운다. */
    suspend fun consume(purchase: TipPurchase): Boolean
}
