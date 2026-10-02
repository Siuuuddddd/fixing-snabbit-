package com.snabbit.prototype.wallet.application

import com.snabbit.prototype.wallet.booking.BookingPaymentQuote
import com.snabbit.prototype.wallet.booking.BookingQuoteClient
import com.snabbit.prototype.wallet.payment.PaymentProvider
import com.snabbit.prototype.wallet.payment.ProviderOrder
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
        every { provider.createOrder(19_900, "plus_${creating.purchaseId}") } returns
            ProviderOrder("order_plus", 19_900, "INR")
        every {
            transactions.attachPlusPurchaseOrder(creating.purchaseId, any())
        } returns pending

        val result = service.createPlusPurchase(
            walletId, customerId, "MONTHLY", 19_900, 30, 4, 3_000, null, "plus-once",
        )

        assertEquals(pending, result)
        verify(exactly = 1) { provider.createOrder(19_900, "plus_${creating.purchaseId}") }
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
}
