package dev.linakbed.app.feature.about

import android.app.Activity
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.linakbed.app.BuildConfig
import dev.linakbed.app.feature.about.TipPurchase.State.PENDING
import dev.linakbed.app.feature.about.TipPurchase.State.PURCHASED
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric

/** S16 커피 한 잔 후원 (ADR-014): 카드 표시·결제 결과·소비 규칙. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class DonationViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val billing = FakeTipBilling()
    private val activity: Activity by lazy { Robolectric.buildActivity(Activity::class.java).get() }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.started(): DonationViewModel = DonationViewModel(billing).also { runCurrent() }

    private fun TestScope.emit(result: TipResult) {
        billing.results.tryEmit(result)
        runCurrent()
    }

    @Test
    fun `shows the Play price when billing and the product are available`() =
        runTest(dispatcher) {
            assertThat(started().uiState.value).isEqualTo(DonationUiState(price = "₩1,500"))
        }

    @Test
    fun `hides the card without Play billing or without the product`() =
        runTest(dispatcher) {
            billing.available = false
            assertThat(started().uiState.value.price).isNull()

            billing.available = true
            billing.productPrice = null
            assertThat(started().uiState.value.price).isNull()
        }

    @Test
    fun `consumes leftover paid purchases on start, but not pending ones`() =
        runTest(dispatcher) {
            billing.leftovers = listOf(TipPurchase("paid", PURCHASED), TipPurchase("waiting", PENDING))

            started()

            assertThat(billing.consumed).containsExactly("paid")
        }

    @Test
    fun `donate opens the Play purchase screen for the coffee product`() =
        runTest(dispatcher) {
            val vm = started()

            vm.donate(activity)

            assertThat(billing.launched).containsExactly(BuildConfig.DONATION_PRODUCT_ID)
            assertThat(BuildConfig.DONATION_PRODUCT_ID).isEqualTo("coffee")
        }

    @Test
    fun `a paid purchase is consumed and thanked`() =
        runTest(dispatcher) {
            val vm = started()

            emit(TipResult.Purchased(listOf(TipPurchase("t1", PURCHASED))))

            assertThat(billing.consumed).containsExactly("t1")
            assertThat(vm.uiState.value.status).isEqualTo(DonationStatus.THANKS)
        }

    @Test
    fun `a pending purchase waits and is not consumed`() =
        runTest(dispatcher) {
            val vm = started()

            emit(TipResult.Purchased(listOf(TipPurchase("t1", PENDING))))

            assertThat(billing.consumed).isEmpty()
            assertThat(vm.uiState.value.status).isEqualTo(DonationStatus.PENDING)
        }

    @Test
    fun `cancelling leaves the card as it was`() =
        runTest(dispatcher) {
            val vm = started()

            emit(TipResult.Cancelled)

            assertThat(vm.uiState.value).isEqualTo(DonationUiState(price = "₩1,500"))
        }

    @Test
    fun `a failed purchase shows a failure and retries leftover consumption`() =
        runTest(dispatcher) {
            val vm = started()
            billing.leftovers = listOf(TipPurchase("owned", PURCHASED))

            emit(TipResult.Failed(code = 7))

            assertThat(vm.uiState.value.status).isEqualTo(DonationStatus.FAILED)
            assertThat(billing.consumed).containsExactly("owned")
        }

    @Test
    fun `a purchase screen that does not open is a failure`() =
        runTest(dispatcher) {
            billing.launchSucceeds = false
            val vm = started()

            vm.donate(activity)
            runCurrent()

            assertThat(vm.uiState.value.status).isEqualTo(DonationStatus.FAILED)
        }

    @Test
    fun `a paid purchase that cannot be consumed is a failure`() =
        runTest(dispatcher) {
            billing.consumeSucceeds = false
            val vm = started()

            emit(TipResult.Purchased(listOf(TipPurchase("t1", PURCHASED))))

            assertThat(vm.uiState.value.status).isEqualTo(DonationStatus.FAILED)
        }

    @Test
    fun `donating again clears the previous message`() =
        runTest(dispatcher) {
            val vm = started()
            emit(TipResult.Purchased(listOf(TipPurchase("t1", PURCHASED))))

            vm.donate(activity)

            assertThat(vm.uiState.value.status).isEqualTo(DonationStatus.IDLE)
        }
}
