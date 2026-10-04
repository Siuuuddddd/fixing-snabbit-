package com.snabbit.prototype.mobile.ui.plus

import com.snabbit.prototype.mobile.domain.payment.CheckoutRequest
import com.snabbit.prototype.mobile.domain.payment.CheckoutResult
import com.snabbit.prototype.mobile.domain.payment.PaymentCheckoutCoordinator
import com.snabbit.prototype.mobile.domain.plus.PlusPlan
import com.snabbit.prototype.mobile.domain.plus.PlusPurchase
import com.snabbit.prototype.mobile.domain.plus.PlusRepository
import com.snabbit.prototype.mobile.domain.plus.PlusStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlusViewModelTest {
    @Test fun `client success waits for backend membership activation`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = FakePlusRepository()
        val coordinator = RecordingCheckoutCoordinator()
        val viewModel = PlusViewModel(repository, coordinator, dispatcher, 0, 1)
        runCurrent()
        viewModel.join(repository.plans().single())
        runCurrent()
        coordinator.report(CheckoutResult.ClientSuccess(coordinator.lastRequest!!.requestId))
        runCurrent()

        assertEquals(PlusPurchasePhase.VERIFYING, viewModel.uiState.value.phase)
        assertFalse(viewModel.uiState.value.status!!.active)

        repository.current = PlusStatus("user", true, "2026-11-03T00:00:00Z", 4)
        viewModel.checkAgain()
        runCurrent()
        assertEquals(PlusPurchasePhase.CONFIRMED, viewModel.uiState.value.phase)
        assertTrue(viewModel.uiState.value.status!!.active)
    }
}

private class FakePlusRepository : PlusRepository {
    var current = PlusStatus("user", false, null, 0)
    private val plan = PlusPlan("MONTHLY", 19_900, 30, 4, 3_000)
    override suspend fun status() = current
    override suspend fun purchase(planCode: String, idempotencyKey: String) = PlusPurchase("purchase", "wallet", "user", planCode, 19_900, "PENDING", "order")
    override fun plans() = listOf(plan)
}

private class RecordingCheckoutCoordinator : PaymentCheckoutCoordinator {
    private val requestEvents = MutableSharedFlow<CheckoutRequest>()
    private val resultEvents = MutableSharedFlow<CheckoutResult>(extraBufferCapacity = 4)
    override val requests = requestEvents
    override val results = resultEvents
    var lastRequest: CheckoutRequest? = null
    override fun request(checkout: CheckoutRequest) { lastRequest = checkout }
    override fun report(result: CheckoutResult) { resultEvents.tryEmit(result) }
}
