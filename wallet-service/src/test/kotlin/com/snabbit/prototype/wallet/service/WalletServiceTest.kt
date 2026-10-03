package com.snabbit.prototype.wallet.service

import com.snabbit.prototype.wallet.dto.response.RefundSnapshot

import com.snabbit.prototype.wallet.dto.response.BookingPaymentQuote

import com.snabbit.prototype.wallet.dto.response.RefundExecution

import com.snabbit.prototype.wallet.dto.response.ProviderRefund

import com.snabbit.prototype.wallet.dto.response.PlusPurchaseSnapshot

import com.snabbit.prototype.wallet.dto.response.ProviderOrder

import com.snabbit.prototype.wallet.dto.response.BookingPaymentSnapshot

import com.snabbit.prototype.wallet.booking.BookingQuoteClient
import com.snabbit.prototype.wallet.payment.PaymentProvider
import com.snabbit.prototype.wallet.payment.PaymentProviderException
import com.snabbit.prototype.wallet.support.PaymentConflict
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class WalletServiceTest {
    private val transactions = mockk<WalletTransactionService>()
    private val provider = mockk<PaymentProvider>()
    private val quotes = mockk<BookingQuoteClient>()
    private val now = Instant.parse("2026-10-02T04:00:00Z")
    private val service = WalletService(transactions, provider, quotes, Clock.fixed(now, ZoneOffset.UTC))

    @Test
    fun `booking payment uses only the authoritative booking quote`() {
        val walletId = UUID.randomUUID()
        val bookingId = UUID.randomUUID()
        val customerId = UUID.randomUUID()
        val expected = payment(walletId, bookingId, 15_000)
        every { transactions.existingBookingPayment(walletId, bookingId, "pay-once") } returns null
        every { quotes.get(bookingId) } returns quote(bookingId, customerId, 15_000)
        every {
            transactions.prepareBookingPayment(walletId, bookingId, customerId, 15_000, true, "pay-once")
        } returns expected

        val result = service.createBookingPayment(walletId, bookingId, true, "pay-once")

        assertEquals(15_000, result.totalAmountMinor)
        verify(exactly = 1) {
            transactions.prepareBookingPayment(walletId, bookingId, customerId, 15_000, true, "pay-once")
        }
        verify(exactly = 0) { provider.createOrder(any(), any()) }
    }

    @Test
    fun `expired booking cannot reserve or charge money`() {
        val walletId = UUID.randomUUID()
        val bookingId = UUID.randomUUID()
        every { transactions.existingBookingPayment(walletId, bookingId, "expired") } returns null
        every { quotes.get(bookingId) } returns quote(bookingId, UUID.randomUUID(), 15_000, now)

        assertThrows(PaymentConflict::class.java) {
            service.createBookingPayment(walletId, bookingId, true, "expired")
        }

        verify(exactly = 0) { transactions.prepareBookingPayment(any(), any(), any(), any(), any(), any()) }
        verify(exactly = 0) { provider.createOrder(any(), any()) }
    }

    @Test
    fun `booking in the wrong lifecycle state cannot reserve or charge money`() {
        val walletId = UUID.randomUUID()
        val bookingId = UUID.randomUUID()
        every { transactions.existingBookingPayment(walletId, bookingId, "already-confirmed") } returns null
        every { quotes.get(bookingId) } returns quote(bookingId, UUID.randomUUID(), 15_000).copy(status = "CONFIRMED")

        assertThrows(PaymentConflict::class.java) {
            service.createBookingPayment(walletId, bookingId, true, "already-confirmed")
        }

        verify(exactly = 0) { transactions.prepareBookingPayment(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `idempotent replay returns original payment after booking state changes`() {
        val walletId = UUID.randomUUID()
        val bookingId = UUID.randomUUID()
        val existing = payment(walletId, bookingId, 15_000)
        every { transactions.existingBookingPayment(walletId, bookingId, "replay") } returns existing

        val result = service.createBookingPayment(walletId, bookingId, true, "replay")

        assertEquals(existing, result)
        verify(exactly = 0) { quotes.get(any()) }
        verify(exactly = 0) { transactions.prepareBookingPayment(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `Plus purchase creates provider order from server supplied plan terms`() {
        val walletId = UUID.randomUUID()
        val customerId = UUID.randomUUID()
        val creating = PlusPurchaseSnapshot(
            UUID.randomUUID(), walletId, customerId, "MONTHLY", 19_900, "CREATING_ORDER", null,
        )
        val pending = creating.copy(status = "PENDING", providerOrderId = "order_plus")
        every {
            transactions.preparePlusPurchase(
                walletId, customerId, "MONTHLY", 19_900, 30, 4, 3_000, null, "plus-once",
            )
        } returns creating
        val receipt = compactReceipt("plus", creating.purchaseId)
        every { provider.findOrderByReceipt(receipt) } returns null
        every { provider.createOrder(19_900, receipt) } returns
            ProviderOrder("order_plus", 19_900, "INR")
        every {
            transactions.attachPlusPurchaseOrder(creating.purchaseId, any())
        } returns pending

        val result = service.createPlusPurchase(
            walletId, customerId, "MONTHLY", 19_900, 30, 4, 3_000, null, "plus-once",
        )

        assertEquals(pending, result)
        verify(exactly = 1) { provider.createOrder(19_900, receipt) }
    }

    @Test
    fun `order retry attaches provider order recovered by receipt without creating another`() {
        val walletId = UUID.randomUUID()
        val customerId = UUID.randomUUID()
        val creating = PlusPurchaseSnapshot(
            UUID.randomUUID(), walletId, customerId, "MONTHLY", 19_900, "CREATING_ORDER", null,
        )
        val recovered = ProviderOrder("order_recovered", 19_900, "INR")
        val pending = creating.copy(status = "PENDING", providerOrderId = recovered.id)
        val receipt = compactReceipt("plus", creating.purchaseId)
        every {
            transactions.preparePlusPurchase(
                walletId, customerId, "MONTHLY", 19_900, 30, 4, 3_000, null, "plus-retry",
            )
        } returns creating
        every { provider.findOrderByReceipt(receipt) } returns recovered
        every { transactions.attachPlusPurchaseOrder(creating.purchaseId, recovered) } returns pending

        val result = service.createPlusPurchase(
            walletId, customerId, "MONTHLY", 19_900, 30, 4, 3_000, null, "plus-retry",
        )

        assertEquals(pending, result)
        verify(exactly = 0) { provider.createOrder(any(), any()) }
    }

    @Test
    fun `ambiguous create response reconciles the provider order before failing locally`() {
        val walletId = UUID.randomUUID()
        val customerId = UUID.randomUUID()
        val creating = PlusPurchaseSnapshot(
            UUID.randomUUID(), walletId, customerId, "MONTHLY", 19_900, "CREATING_ORDER", null,
        )
        val recovered = ProviderOrder("order_after_timeout", 19_900, "INR")
        val pending = creating.copy(status = "PENDING", providerOrderId = recovered.id)
        val receipt = compactReceipt("plus", creating.purchaseId)
        every {
            transactions.preparePlusPurchase(
                walletId, customerId, "MONTHLY", 19_900, 30, 4, 3_000, null, "plus-timeout",
            )
        } returns creating
        every { provider.findOrderByReceipt(receipt) } returnsMany listOf(null, recovered)
        every { provider.createOrder(19_900, receipt) } throws PaymentProviderException("timeout")
        every { transactions.attachPlusPurchaseOrder(creating.purchaseId, recovered) } returns pending

        val result = service.createPlusPurchase(
            walletId, customerId, "MONTHLY", 19_900, 30, 4, 3_000, null, "plus-timeout",
        )

        assertEquals(pending, result)
    }

    @Test
    fun `failed reconciliation leaves creating order retriable`() {
        val walletId = UUID.randomUUID()
        val customerId = UUID.randomUUID()
        val creating = PlusPurchaseSnapshot(
            UUID.randomUUID(), walletId, customerId, "MONTHLY", 19_900, "CREATING_ORDER", null,
        )
        val receipt = compactReceipt("plus", creating.purchaseId)
        val createFailure = PaymentProviderException("timeout")
        every {
            transactions.preparePlusPurchase(
                walletId, customerId, "MONTHLY", 19_900, 30, 4, 3_000, null, "plus-unavailable",
            )
        } returns creating
        every { provider.findOrderByReceipt(receipt) } returns null andThenThrows
            PaymentProviderException("lookup unavailable")
        every { provider.createOrder(19_900, receipt) } throws createFailure

        val thrown = assertThrows(PaymentProviderException::class.java) {
            service.createPlusPurchase(
                walletId, customerId, "MONTHLY", 19_900, 30, 4, 3_000, null, "plus-unavailable",
            )
        }

        assertEquals(createFailure, thrown)
        assertEquals(1, thrown.suppressed.size)
    }

    @Test
    fun `absent order after create error remains retriable for eventual consistency`() {
        val walletId = UUID.randomUUID()
        val customerId = UUID.randomUUID()
        val creating = PlusPurchaseSnapshot(
            UUID.randomUUID(), walletId, customerId, "MONTHLY", 19_900, "CREATING_ORDER", null,
        )
        val receipt = compactReceipt("plus", creating.purchaseId)
        every {
            transactions.preparePlusPurchase(
                walletId, customerId, "MONTHLY", 19_900, 30, 4, 3_000, null, "plus-failed",
            )
        } returns creating
        every { provider.findOrderByReceipt(receipt) } returns null
        every { provider.createOrder(19_900, receipt) } throws PaymentProviderException("declined")

        assertThrows(PaymentProviderException::class.java) {
            service.createPlusPurchase(
                walletId, customerId, "MONTHLY", 19_900, 30, 4, 3_000, null, "plus-failed",
            )
        }

        verify(exactly = 2) { provider.findOrderByReceipt(receipt) }
    }

    @Test
    fun `refund uses the durable refund id as provider reconciliation reference`() {
        val refundId = UUID.randomUUID()
        val execution = RefundExecution(refundId, "pay_captured", 12_500)
        val providerRefund = ProviderRefund("rfnd_123", 12_500)
        val completed = RefundSnapshot(
            refundId, UUID.randomUUID(), "COMPLETED", 0, 12_500, providerRefund.id,
        )
        every { transactions.claimRefund(refundId) } returns execution
        every { provider.refund("pay_captured", 12_500, refundId.toString()) } returns providerRefund
        every { transactions.completeRefund(refundId, providerRefund) } returns completed

        val result = service.executeRefund(refundId)

        assertEquals(completed, result)
        verify(exactly = 1) { provider.refund("pay_captured", 12_500, refundId.toString()) }
    }

    @Test
    fun `concurrent refund execution reports the active processing lease`() {
        val refundId = UUID.randomUUID()
        every { transactions.claimRefund(refundId) } returns null
        every { transactions.refund(refundId) } returns RefundSnapshot(
            refundId, UUID.randomUUID(), "PROCESSING", 0, 12_500, null,
        )

        assertThrows(PaymentConflict::class.java) { service.executeRefund(refundId) }

        verify(exactly = 0) { provider.refund(any(), any(), any()) }
    }

    private fun quote(
        bookingId: UUID,
        customerId: UUID,
        amountMinor: Long,
        expiresAt: Instant = now.plusSeconds(300),
    ) = BookingPaymentQuote(
        bookingId,
        customerId,
        "AWAITING_PAYMENT",
        amountMinor,
        "INR",
        expiresAt,
    )

    private fun payment(walletId: UUID, bookingId: UUID, amountMinor: Long) = BookingPaymentSnapshot(
        UUID.randomUUID(),
        bookingId,
        walletId,
        amountMinor,
        amountMinor,
        0,
        "COMPLETED",
        null,
        null,
    )

    private fun compactReceipt(kind: String, id: UUID): String =
        "${kind}_${id.toString().replace("-", "")}"
}
