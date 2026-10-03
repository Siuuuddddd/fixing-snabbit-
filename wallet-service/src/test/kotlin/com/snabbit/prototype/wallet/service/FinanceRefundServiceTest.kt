package com.snabbit.prototype.wallet.service

import com.snabbit.prototype.wallet.dto.response.RefundSnapshot

import com.snabbit.prototype.wallet.dto.response.FinanceRefundSnapshot

import com.snabbit.prototype.wallet.domain.enums.RefundStatus

import com.snabbit.prototype.wallet.payment.PaymentProviderException
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.util.UUID

class FinanceRefundServiceTest {
    private val transactions = mockk<WalletTransactionService>()
    private val walletService = mockk<WalletService>()
    private val audit = mockk<FinanceRefundAuditService>()
    private val service = FinanceRefundService(transactions, walletService, audit)

    @Test
    fun `refund queue delegates the optional status filter`() {
        val expected = emptyList<FinanceRefundSnapshot>()
        every { transactions.financeRefunds(RefundStatus.CREATING) } returns expected

        assertEquals(expected, service.list(RefundStatus.CREATING))

        verify(exactly = 1) { transactions.financeRefunds(RefundStatus.CREATING) }
    }

    @Test
    fun `retry uses the stored refund and records the authenticated staff action`() {
        val refundId = UUID.randomUUID()
        val staffId = UUID.randomUUID()
        val actionId = UUID.randomUUID()
        val expected = refund(refundId)
        every { audit.start(refundId, staffId) } returns actionId
        every { walletService.executeRefund(refundId) } returns expected
        every { audit.succeed(actionId) } just runs

        val result = service.retry(refundId, staffId)

        assertEquals(actionId, result.actionId)
        assertEquals(expected, result.refund)
        verify(exactly = 1) { walletService.executeRefund(refundId) }
        verify(exactly = 1) { audit.succeed(actionId) }
        verify(exactly = 0) { audit.fail(any(), any()) }
    }

    @Test
    fun `failed provider retry is audited and propagated`() {
        val refundId = UUID.randomUUID()
        val staffId = UUID.randomUUID()
        val actionId = UUID.randomUUID()
        val failure = PaymentProviderException("provider unavailable")
        every { audit.start(refundId, staffId) } returns actionId
        every { walletService.executeRefund(refundId) } throws failure
        every { audit.fail(actionId, failure) } just runs

        val thrown = assertThrows(PaymentProviderException::class.java) {
            service.retry(refundId, staffId)
        }

        assertEquals(failure, thrown)
        verify(exactly = 1) { audit.fail(actionId, failure) }
        verify(exactly = 0) { audit.succeed(any()) }
    }

    private fun refund(refundId: UUID) = RefundSnapshot(
        refundId = refundId,
        bookingPaymentId = UUID.randomUUID(),
        status = RefundStatus.COMPLETED.name,
        walletAmountMinor = 5_000,
        onlineAmountMinor = 10_000,
        providerRefundId = "rfnd_123",
    )
}
