package com.snabbit.prototype.wallet.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.snabbit.prototype.wallet.config.RazorpayProperties
import com.snabbit.prototype.wallet.config.WalletProperties
import com.snabbit.prototype.wallet.domain.BookingPayment
import com.snabbit.prototype.wallet.domain.enums.BookingPaymentStatus
import com.snabbit.prototype.wallet.domain.enums.PlusPurchaseStatus
import com.snabbit.prototype.wallet.domain.enums.RefundStatus
import com.snabbit.prototype.wallet.domain.enums.TopUpStatus
import com.snabbit.prototype.wallet.domain.PaymentRefund
import com.snabbit.prototype.wallet.domain.PlusPurchase
import com.snabbit.prototype.wallet.domain.TopUpOrder
import com.snabbit.prototype.wallet.domain.WalletAccount
import com.snabbit.prototype.wallet.domain.WalletLedgerEntry
import com.snabbit.prototype.wallet.domain.WalletReservation
import com.snabbit.prototype.wallet.dto.response.ProviderCapture
import com.snabbit.prototype.wallet.exception.PaymentConflict
import com.snabbit.prototype.wallet.exception.WalletAccessDenied
import com.snabbit.prototype.wallet.exception.WalletRuleViolation
import com.snabbit.prototype.wallet.repository.BookingPaymentRepository
import com.snabbit.prototype.wallet.repository.PaymentRefundRepository
import com.snabbit.prototype.wallet.repository.PlusPurchaseRepository
import com.snabbit.prototype.wallet.repository.ProcessedWebhookStore
import com.snabbit.prototype.wallet.repository.TopUpOrderRepository
import com.snabbit.prototype.wallet.repository.WalletAccountRepository
import com.snabbit.prototype.wallet.repository.WalletLedgerRepository
import com.snabbit.prototype.wallet.repository.WalletOutboxRepository
import com.snabbit.prototype.wallet.repository.WalletReservationRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class WalletTransactionServiceTest {
    private val walletRepository = mockk<WalletAccountRepository>()
    private val ledgerRepository = mockk<WalletLedgerRepository>()
    private val reservationRepository = mockk<WalletReservationRepository>()
    private val topUpRepository = mockk<TopUpOrderRepository>()
    private val plusPurchaseRepository = mockk<PlusPurchaseRepository>()
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
        refundProcessingLease = Duration.ofMinutes(5),
        razorpay = RazorpayProperties("fake", "https://example.invalid", "", "", "secret"),
    )
    private val service = WalletTransactionService(
        walletRepository,
        ledgerRepository,
        reservationRepository,
        topUpRepository,
        plusPurchaseRepository,
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
            wallet.customerId,
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
    fun `wallet must belong to the booking customer`() {
        val wallet = wallet()
        every { walletRepository.findForUpdate(wallet.id) } returns wallet

        assertThrows(PaymentConflict::class.java) {
            service.prepareBookingPayment(
                wallet.id,
                UUID.randomUUID(),
                UUID.randomUUID(),
                8_000,
                useWallet = true,
                idempotencyKey = "wrong-owner",
            )
        }

        verify(exactly = 0) { paymentRepository.save(any()) }
        verify(exactly = 0) { reservationRepository.save(any()) }
    }

    @Test
    fun `money inputs reject sub-rupee values that Razorpay cannot refund`() {
        assertThrows(WalletRuleViolation::class.java) {
            service.prepareTopUp(UUID.randomUUID(), 50, "tiny-topup")
        }

        verify(exactly = 0) { walletRepository.findForUpdate(any()) }
    }

    @Test
    fun `customer routes reject a wallet owned by another customer`() {
        val wallet = wallet()
        every { walletRepository.findById(wallet.id) } returns java.util.Optional.of(wallet)

        assertThrows(WalletAccessDenied::class.java) {
            service.requireOwnership(wallet.id, UUID.randomUUID())
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
        every {
            webhookStore.claim("RAZORPAY", "event_1", "payment.captured", "order_1", "pay_1", 5_000, now)
        } returns true andThen false
        every { webhookStore.complete("RAZORPAY", "event_1", now) } returns Unit
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
        every {
            webhookStore.claim(
                "RAZORPAY", "event_late", "payment.captured", "order_late", "pay_late", 5_000, now,
            )
        } returns true
        every { webhookStore.complete("RAZORPAY", "event_late", now) } returns Unit
        every { topUpRepository.findByProviderOrderIdForUpdate("order_late") } returns null
        every { plusPurchaseRepository.findByProviderOrderIdForUpdate("order_late") } returns null
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

    @Test
    fun `verified Plus capture publishes one entitlement event without crediting wallet`() {
        val wallet = wallet()
        val purchase = PlusPurchase(
            UUID.randomUUID(), wallet.id, wallet.customerId, "MONTHLY", 19_900,
            30, 4, 3_000, null, PlusPurchaseStatus.PENDING, "RAZORPAY",
            providerOrderId = "order_plus", idempotencyKey = "plus-once",
            createdAt = now, updatedAt = now,
        )
        val capture = ProviderCapture("event_plus", "payment.captured", "order_plus", "pay_plus", 19_900)
        every {
            webhookStore.claim(
                "RAZORPAY", "event_plus", "payment.captured", "order_plus", "pay_plus", 19_900, now,
            )
        } returns true
        every { webhookStore.complete("RAZORPAY", "event_plus", now) } returns Unit
        every { topUpRepository.findByProviderOrderIdForUpdate("order_plus") } returns null
        every { plusPurchaseRepository.findByProviderOrderIdForUpdate("order_plus") } returns purchase
        every { outboxRepository.save(any()) } answers { firstArg() }

        val result = service.processCapture(capture)

        assertEquals("PLUS_SUBSCRIPTION_PURCHASED", result.result)
        assertEquals(PlusPurchaseStatus.PAID, purchase.status)
        assertEquals("pay_plus", purchase.providerPaymentId)
        verify(exactly = 0) { ledgerRepository.save(any()) }
        verify(exactly = 1) {
            outboxRepository.save(match { it.eventType == "PlusSubscriptionPurchased" })
        }
    }

    @Test
    fun `capture for an order not attached yet remains pending`() {
        val capture = ProviderCapture(
            "event_early", "payment.captured", "order_not_attached", "pay_early", 5_000,
        )
        every {
            webhookStore.claim(
                "RAZORPAY", "event_early", "payment.captured",
                "order_not_attached", "pay_early", 5_000, now,
            )
        } returns true
        every { topUpRepository.findByProviderOrderIdForUpdate("order_not_attached") } returns null
        every { plusPurchaseRepository.findByProviderOrderIdForUpdate("order_not_attached") } returns null
        every { paymentRepository.findByProviderOrderIdForUpdate("order_not_attached") } returns null

        val result = service.processCapture(capture)

        assertEquals("PENDING_UNKNOWN_ORDER", result.result)
        verify(exactly = 0) { webhookStore.complete(any(), any(), any()) }
    }

    @Test
    fun `customer cancellation refunds requested total and retains fee from online portion first`() {
        val payment = BookingPayment(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null,
            15_000, 10_000, 5_000, BookingPaymentStatus.COMPLETED,
            provider = "RAZORPAY", providerPaymentId = "pay_captured", idempotencyKey = "booking-payment",
            createdAt = now, updatedAt = now,
        )
        val saved = slot<PaymentRefund>()
        val sourceEventId = UUID.randomUUID()
        every { paymentRepository.findByBookingIdForUpdate(payment.bookingId) } returns payment
        every { refundRepository.totalRequested(payment.id) } returns 0
        every { refundRepository.findAllByBookingPaymentId(payment.id) } returns emptyList()
        every { refundRepository.save(capture(saved)) } answers { firstArg() }
        every { outboxRepository.save(any()) } answers { firstArg() }

        val refundId = service.terminateBookingPayment(payment.bookingId, sourceEventId, 12_500)

        assertEquals(saved.captured.id, refundId)
        assertEquals(10_000, saved.captured.walletAmountMinor)
        assertEquals(2_500, saved.captured.onlineAmountMinor)
        assertEquals(BookingPaymentStatus.COMPLETED, payment.status)
    }

    @Test
    fun `stale processing refund is reclaimed after a crash but an active claim is not`() {
        val payment = BookingPayment(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null,
            5_000, 0, 5_000, BookingPaymentStatus.REFUND_PENDING,
            provider = "RAZORPAY", providerPaymentId = "pay_captured", idempotencyKey = "payment",
            createdAt = now.minusSeconds(600), updatedAt = now.minusSeconds(600),
        )
        val refund = PaymentRefund(
            UUID.randomUUID(), payment.id, payment.walletId, 0, 5_000, RefundStatus.PROCESSING,
            null, "refund-once", now.minusSeconds(600), now.minusSeconds(301),
        )
        every { refundRepository.findForUpdate(refund.id) } returns refund
        every { paymentRepository.findById(payment.id) } returns java.util.Optional.of(payment)

        val reclaimed = service.claimRefund(refund.id)
        val stillActive = service.claimRefund(refund.id)

        assertEquals(refund.id, reclaimed?.refundId)
        assertEquals(now, refund.updatedAt)
        assertEquals(null, stillActive)
        verify(exactly = 1) { paymentRepository.findById(payment.id) }
    }

    @Test
    fun `refund retry queue includes abandoned processing leases`() {
        val refund = PaymentRefund(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 0, 5_000, RefundStatus.PROCESSING,
            null, "refund-queued", now.minusSeconds(600), now.minusSeconds(301),
        )
        every {
            refundRepository.findRetryable(
                RefundStatus.CREATING,
                RefundStatus.PROCESSING,
                now.minusSeconds(300),
                match { it.pageSize == 100 },
            )
        } returns listOf(refund)

        assertEquals(listOf(refund.id), service.pendingRefundIds())
    }

    private fun wallet() = WalletAccount(
        id = UUID.randomUUID(),
        customerId = UUID.randomUUID(),
        currency = "INR",
        createdAt = now,
    )
}
