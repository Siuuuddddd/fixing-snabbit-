package com.snabbit.prototype.mobile.ui.wallet

import com.snabbit.prototype.mobile.domain.payment.CheckoutRequest
import com.snabbit.prototype.mobile.domain.payment.CheckoutResult
import com.snabbit.prototype.mobile.domain.payment.PaymentCheckoutCoordinator
import com.snabbit.prototype.mobile.domain.wallet.WalletBalance
import com.snabbit.prototype.mobile.domain.wallet.WalletLedgerEntry
import com.snabbit.prototype.mobile.domain.wallet.WalletRepository
import com.snabbit.prototype.mobile.domain.wallet.WalletTopUp
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WalletViewModelTest {
    @Test fun `client success keeps old balance until backend confirms`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = FakeWalletRepository()
        val coordinator = RecordingCheckoutCoordinator()
        val viewModel = WalletViewModel(repository, coordinator, dispatcher, 0, 1)
        runCurrent()

        viewModel.updateAmount("100")
        viewModel.addMoney()
        runCurrent()
        val requestId = coordinator.lastRequest!!.requestId
        coordinator.report(CheckoutResult.ClientSuccess(requestId))
        runCurrent()

        assertEquals(AddMoneyPhase.VERIFYING, viewModel.uiState.value.phase)
        assertEquals(5_000L, viewModel.uiState.value.balance?.availableMinor)

        repository.current = balance(15_000)
        viewModel.checkAgain()
        runCurrent()
        assertEquals(AddMoneyPhase.CONFIRMED, viewModel.uiState.value.phase)
        assertEquals(15_000L, viewModel.uiState.value.balance?.availableMinor)
    }

    @Test fun `client failure does not change backend balance`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = FakeWalletRepository()
        val coordinator = RecordingCheckoutCoordinator()
        val viewModel = WalletViewModel(repository, coordinator, dispatcher, 0, 1)
        runCurrent()
        viewModel.addMoney()
        runCurrent()
        coordinator.report(CheckoutResult.ClientFailure(coordinator.lastRequest!!.requestId, 1, "cancelled"))
        runCurrent()
        assertEquals(AddMoneyPhase.FAILED, viewModel.uiState.value.phase)
        assertEquals(5_000L, viewModel.uiState.value.balance?.availableMinor)
        assertTrue(repository.entries.last().state.name == "FAILED")
    }
}

private fun balance(amount: Long) = WalletBalance("wallet", "INR", amount, 0, amount, 0)

private class RecordingCheckoutCoordinator : PaymentCheckoutCoordinator {
    private val requestEvents = MutableSharedFlow<CheckoutRequest>()
    private val resultEvents = MutableSharedFlow<CheckoutResult>(extraBufferCapacity = 4)
    override val requests = requestEvents
    override val results = resultEvents
    var lastRequest: CheckoutRequest? = null
    override fun request(checkout: CheckoutRequest) { lastRequest = checkout }
    override fun report(result: CheckoutResult) { resultEvents.tryEmit(result) }
}

private class FakeWalletRepository : WalletRepository {
    var current = balance(5_000)
    val entries = mutableListOf<WalletLedgerEntry>()
    override suspend fun balance() = current
    override suspend fun createTopUp(amountMinor: Long, idempotencyKey: String) = WalletTopUp("top-up", "wallet", amountMinor, "PENDING", "order")
    override suspend fun history() = entries.toList()
    override suspend fun recordPrototypeEntry(entry: WalletLedgerEntry) { entries.removeAll { it.id == entry.id }; entries += entry }
}
