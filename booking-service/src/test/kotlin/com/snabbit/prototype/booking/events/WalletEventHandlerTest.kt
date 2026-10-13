package com.snabbit.prototype.booking.events

import com.fasterxml.jackson.databind.ObjectMapper
import com.snabbit.prototype.booking.service.BookingLifecycleService
import com.snabbit.prototype.booking.service.HomeContactService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import org.junit.jupiter.api.Test

class WalletEventHandlerTest {
    private val processed = mockk<ProcessedMessageStore>()
    private val bookingService = mockk<BookingLifecycleService>()
    private val homeContacts = mockk<HomeContactService>(relaxed = true)
    private val now = Instant.parse("2026-10-02T04:00:00Z")
    private val properties = EventingProperties(
        "booking-events",
        "wallet-events",
        "booking-service",
        Duration.ofSeconds(1),
        Duration.ofSeconds(30),
        50,
    )
    private val handler = WalletEventHandler(
        processed,
        bookingService,
        homeContacts,
        properties,
        Clock.fixed(now, ZoneOffset.UTC),
    )

    @Test
    fun `payment success confirms booking exactly once`() {
        val bookingId = UUID.randomUUID()
        val event = EventEnvelope(
            eventId = UUID.randomUUID(),
            eventType = "WalletPaymentSucceeded",
            aggregateType = "Wallet",
            aggregateId = bookingId.toString(),
            occurredAt = now,
            payload = ObjectMapper().createObjectNode().put("paymentId", "payment-1"),
        )
        every { processed.claim("booking-service", event.eventId, now) } returns true andThen false
        every { bookingService.confirmPayment(bookingId, "wallet:payment-1") } returns mockk()

        handler.handle(event)
        handler.handle(event)

        verify(exactly = 1) { bookingService.confirmPayment(bookingId, "wallet:payment-1") }
        verify(exactly = 1) { homeContacts.ensureResolvedForConfirmation(bookingId) }
    }

    @Test
    fun `only UUID visit refund keys complete a visit refund`() {
        val bookingId = UUID.randomUUID()
        val refundKey = UUID.randomUUID()
        val visitRefund = EventEnvelope(
            UUID.randomUUID(), "WalletRefundCompleted", "Wallet", bookingId.toString(), now,
            ObjectMapper().createObjectNode().put("idempotencyKey", refundKey.toString()),
        )
        val wholeBookingRefund = visitRefund.copy(
            eventId = UUID.randomUUID(),
            payload = ObjectMapper().createObjectNode().put("idempotencyKey", "booking-termination:${UUID.randomUUID()}"),
        )
        every { processed.claim("booking-service", visitRefund.eventId, now) } returns true
        every { processed.claim("booking-service", wholeBookingRefund.eventId, now) } returns true
        every { bookingService.completeVisitRefund(refundKey) } returns Unit

        handler.handle(visitRefund)
        handler.handle(wholeBookingRefund)

        verify(exactly = 1) { bookingService.completeVisitRefund(refundKey) }
    }
}
