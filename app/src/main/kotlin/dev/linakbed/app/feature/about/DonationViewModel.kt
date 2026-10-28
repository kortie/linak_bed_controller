package dev.linakbed.app.feature.about

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.linakbed.app.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class DonationStatus { IDLE, THANKS, PENDING, FAILED }

/** 후원 카드 상태. [price]가 null이면 카드를 숨긴다 (Play를 쓸 수 없음). */
data class DonationUiState(
    val price: String? = null,
    val status: DonationStatus = DonationStatus.IDLE,
)

/**
 * 커피 한 잔 후원 (ADR-014). 결제가 끝나면 바로 소비해 다시 후원할 수 있게 한다.
 * 소비하지 못한 구매는 화면을 다시 열 때(또는 결제가 실패했을 때) 다시 소비한다.
 */
@HiltViewModel
class DonationViewModel
    @Inject
    constructor(
        private val billing: TipBilling,
    ) : ViewModel() {
        private val state = MutableStateFlow(DonationUiState())
        val uiState: StateFlow<DonationUiState> = state.asStateFlow()

        init {
            viewModelScope.launch { billing.results.collect(::onResult) }
            viewModelScope.launch {
                if (billing.connect()) {
                    consumeLeftovers()
                    val price = billing.price(PRODUCT_ID)
                    state.update { it.copy(price = price) }
                }
            }
        }

        fun donate(activity: Activity) {
            state.update { it.copy(status = DonationStatus.IDLE) }
            if (!billing.launch(activity, PRODUCT_ID)) fail()
        }

        private suspend fun onResult(result: TipResult) {
            when (result) {
                is TipResult.Purchased -> onPurchased(result.purchases)
                TipResult.Cancelled -> Unit
                is TipResult.Failed -> fail()
            }
        }

        private suspend fun onPurchased(purchases: List<TipPurchase>) {
            val paid = purchases.filter { it.state == TipPurchase.State.PURCHASED }
            val status =
                when {
                    paid.isNotEmpty() -> if (paid.all { billing.consume(it) }) DonationStatus.THANKS else DonationStatus.FAILED
                    purchases.isNotEmpty() -> DonationStatus.PENDING
                    else -> DonationStatus.IDLE
                }
            state.update { it.copy(status = status) }
        }

        /** 실패 안내. 이미 가진 상품이라 막힌 경우(ITEM_ALREADY_OWNED)를 풀려고 남은 구매도 소비한다. */
        private fun fail() {
            state.update { it.copy(status = DonationStatus.FAILED) }
            viewModelScope.launch { consumeLeftovers() }
        }

        private suspend fun consumeLeftovers() {
            billing
                .unconsumed(PRODUCT_ID)
                .filter { it.state == TipPurchase.State.PURCHASED }
                .forEach { billing.consume(it) }
        }

        private companion object {
            val PRODUCT_ID: String = BuildConfig.DONATION_PRODUCT_ID
        }
    }
