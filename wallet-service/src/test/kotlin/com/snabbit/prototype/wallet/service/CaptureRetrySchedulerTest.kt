package com.snabbit.prototype.wallet.service

import com.snabbit.prototype.wallet.dto.response.ProviderCapture
import com.snabbit.prototype.wallet.dto.response.WebhookOutcome
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class CaptureRetrySchedulerTest {
    private val transactions = mockk<WalletTransactionService>()
    private val scheduler = CaptureRetryScheduler(transactions)

    @Test
    fun `one failed capture does not prevent later captures from retrying`() {
        val failed = ProviderCapture(
            "event_failed", "payment.captured", "order_failed", "pay_failed", 5_000,
        )
        val recovered = ProviderCapture(
            "event_recovered", "payment.captured", "order_recovered", "pay_recovered", 5_000,
        )
        every { transactions.pendingCaptures() } returns listOf(failed, recovered)
        every { transactions.processCapture(failed) } throws IllegalStateException("still unavailable")
        every { transactions.processCapture(recovered) } returns WebhookOutcome("TOP_UP_CREDITED")

        scheduler.retryPendingCaptures()

        verify(exactly = 1) { transactions.processCapture(failed) }
        verify(exactly = 1) { transactions.processCapture(recovered) }
    }
}
