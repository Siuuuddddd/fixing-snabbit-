package com.snabbit.prototype.wallet.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.snabbit.prototype.wallet.config.RazorpayProperties
import com.snabbit.prototype.wallet.config.WalletProperties
import com.snabbit.prototype.wallet.domain.BookingPayment
import com.snabbit.prototype.wallet.domain.BookingPaymentStatus
import com.snabbit.prototype.wallet.domain.TopUpOrder
import com.snabbit.prototype.wallet.domain.TopUpStatus
import com.snabbit.prototype.wallet.domain.WalletAccount
import com.snabbit.prototype.wallet.domain.WalletLedgerEntry
import com.snabbit.prototype.wallet.domain.WalletReservation
import com.snabbit.prototype.wallet.domain.PaymentRefund
import com.snabbit.prototype.wallet.persistence.BookingPaymentRepository
import com.snabbit.prototype.wallet.persistence.PaymentRefundRepository
import com.snabbit.prototype.wallet.persistence.ProcessedWebhookStore
import com.snabbit.prototype.wallet.persistence.TopUpOrderRepository
import com.snabbit.prototype.wallet.persistence.WalletAccountRepository
import com.snabbit.prototype.wallet.persistence.WalletLedgerRepository
import com.snabbit.prototype.wallet.persistence.WalletOutboxRepository
import com.snabbit.prototype.wallet.persistence.WalletReservationRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class WalletTransactionServiceTest {
    private val walletRepository = mockk<WalletAccountRepository>()
    private val ledgerRepository = mockk<WalletLedgerRepository>()
    private val reservationRepository = mockk<WalletReservationRepository>()
    private val topUpRepository = mockk<TopUpOrderRepository>()
    private val paymentRepository = mockk<BookingPaymentRepository>()
    private val refundRepository = mockk<PaymentRefundRepository>()
    private val webhookStore = mockk<ProcessedWebhookStore>()
    private val outboxRepository = mockk<WalletOutboxRepository>()
    private val now = Instant.parse("2026-10-02T04:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val properties = WalletProperties(
        currency = "INR",
        reservationDuration = Duration.ofMinutes(5),
        refundRetryDelay = Duration.ofMinutes(1),
        razorpay = RazorpayProperties("fake", "https://example.invalid", "", "", "secret"),
    )
    private val service = WalletTransactionService(
        walletRepository,
        ledgerRepository,
        reservationRepository,
        topUpRepository,
        paymentRepository,
        refundRepository,
        webhookStore,
        WalletOutboxWriter(outboxRepository, ObjectMapper().findAndRegisterModules()),
        properties,
        clock,
    )

    @Test
    fun `wallet-only payment captures reservation and appends a debit`() {
        val wallet = wallet()
        val savedReservation = slot<WalletReservation>()
        every { walletRepository.findForUpdate(wallet.id) } returns wallet
        every { paymentRepository.findByIdempotencyKey("pay-once") } returns null
        every { ledgerRepository.netBalance(wallet.id) } returns 10_000
        every { reservationRepository.activeTotal(wallet.id, now) } returns 0
        every { reservationRepository.save(capture(savedReservation)) } answers { firstArg() }
        every { paymentRepository.save(any()) } answers { firstArg() }
        every { reservationRepository.findForUpdate(any()) } answers { savedReservation.captured }
        every { reservationRepository.findById(any()) } answers { java.util.Optional.of(savedReservation.captured) }
        every { ledgerRepository.existsByIdempotencyKey(any()) } returns false
        every { ledgerRepository.save(any()) } answers { firstArg() }
        every { outboxRepository.save(any()) } answers { firstArg() }

        val result = service.prepareBookingPayment(
            wallet.id,
            UUID.randomUUID(),
            8_000,
            useWallet = true,
            idempotencyKey = "pay-once",
        )

        assertEquals(BookingPaymentStatus.COMPLETED.name, result.status)
        assertEquals(8_000, result.walletAmountMinor)
        assertEquals(0, result.onlineAmountMinor)
        verify(exactly = 1) {
            ledgerRepository.save(match<WalletLedgerEntry> { it.amountMinor == 8_000L && it.entryType.name == "DEBIT" })
        }
    }

    @Test
    fun `verified top-up capture credits once and duplicate event does nothing`() {
        val wallet = wallet()
        val topUp = TopUpOrder(
            id = UUID.randomUUID(),
            walletId = wallet.id,
            amountMinor = 5_000,
            status = TopUpStatus.PENDING,
            provider = "RAZORPAY",
            providerOrderId = "order_1",
            idempotencyKey = "topup-once",
            createdAt = now,
            updatedAt = now,
        )
        val capture = ProviderCapture("event_1", "payment.captured", "order_1", "pay_1", 5_000)
        every { webhookStore.claim("RAZORPAY", "event_1", "payment.captured", now) } returns true andThen false
        every { topUpRepository.findByProviderOrderIdForUpdate("order_1") } returns topUp
        every { ledgerRepository.existsByIdempotencyKey(any()) } returns false
        every { ledgerRepository.save(any()) } answers { firstArg() }
        every { outboxRepository.save(any()) } answers { firstArg() }

        val first = service.processCapture(capture)
        val duplicate = service.processCapture(capture)

        assertEquals("TOP_UP_CREDITED", first.result)
        assertEquals("DUPLICATE", duplicate.result)
        verify(exactly = 1) { ledgerRepository.save(any()) }
    }

    @Test
    fun `capture after cancellation creates a durable compensation refund`() {
        val wallet = wallet()
        val payment = BookingPayment(
            id = UUID.randomUUID(),
            bookingId = UUID.randomUUID(),
            walletId = wallet.id,
            reservationId = null,
            totalAmountMinor = 5_000,
            walletAmountMinor = 0,
            onlineAmountMinor = 5_000,
            status = BookingPaymentStatus.FAILED,
            provider = "RAZORPAY",
            providerOrderId = "order_late",
            idempotencyKey = "late-payment",
            createdAt = now,
            updatedAt = now,
        )
        val capture = ProviderCapture("event_late", "payment.captured", "order_late", "pay_late", 5_000)
        every { webhookStore.claim("RAZORPAY", "event_late", "payment.captured", now) } returns true
        every { topUpRepository.findByProviderOrderIdForUpdate("order_late") } returns null
        every { paymentRepository.findByProviderOrderIdForUpdate("order_late") } returns payment
        every { refundRepository.findByBookingPaymentId(payment.id) } returns null
        every { refundRepository.save(any()) } answers { firstArg() }
        every { outboxRepository.save(any()) } answers { firstArg() }

        val result = service.processCapture(capture)

        assertEquals("REFUND_REQUIRED", result.result)
        assertEquals(BookingPaymentStatus.REFUND_PENDING, payment.status)
        assertEquals("pay_late", payment.providerPaymentId)
        verify(exactly = 1) { refundRepository.save(any<PaymentRefund>()) }
    }

    private fun wallet() = WalletAccount(
        id = UUID.randomUUID(),
        customerId = UUID.randomUUID(),
        currency = "INR",
        createdAt = now,
    )
}
