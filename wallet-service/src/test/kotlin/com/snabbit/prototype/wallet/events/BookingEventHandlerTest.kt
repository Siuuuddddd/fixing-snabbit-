package com.snabbit.prototype.wallet.events

import com.fasterxml.jackson.databind.ObjectMapper
import com.snabbit.prototype.wallet.service.WalletTransactionService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class BookingEventHandlerTest {
    private val processed = mockk<ProcessedMessageStore>()
    private val transactions = mockk<WalletTransactionService>()
    private val now = Instant.parse("2026-10-02T04:00:00Z")
    private val properties = EventingProperties(
        "wallet-events",
        "booking-events",
        "wallet-service",
        Duration.ofSeconds(1),
        Duration.ofSeconds(30),
        50,
    )
    private val handler = BookingEventHandler(processed, transactions, properties, Clock.fixed(now, ZoneOffset.UTC))

    @Test
    fun `booking expiry queues refund exactly once`() {
        val bookingId = UUID.randomUUID()
        val refundId = UUID.randomUUID()
        val event = EventEnvelope(
            eventId = UUID.randomUUID(),
            eventType = "BookingExpired",
            aggregateType = "Booking",
            aggregateId = bookingId.toString(),
            occurredAt = now,
            payload = ObjectMapper().createObjectNode(),
        )
        every { processed.claim("wallet-service", event.eventId, now) } returns true andThen false
        every { transactions.terminateBookingPayment(bookingId, event.eventId) } returns refundId

        assertEquals(refundId, handler.handle(event))
        assertEquals(null, handler.handle(event))

        verify(exactly = 1) { transactions.terminateBookingPayment(bookingId, event.eventId) }
    }

    @Test
    fun `customer cancellation forwards authoritative partial refund amount`() {
        val bookingId = UUID.randomUUID()
        val event = EventEnvelope(
            UUID.randomUUID(), "BookingCustomerCancelled", "Booking", bookingId.toString(), now,
            ObjectMapper().createObjectNode().put("refundAmountMinor", 12_500),
        )
        every { processed.claim("wallet-service", event.eventId, now) } returns true
        every { transactions.terminateBookingPayment(bookingId, event.eventId, 12_500) } returns UUID.randomUUID()

        handler.handle(event)

        verify(exactly = 1) { transactions.terminateBookingPayment(bookingId, event.eventId, 12_500) }
    }

    @Test
    fun `platform cancellation forwards full refund amount`() {
        val bookingId = UUID.randomUUID()
        val event = EventEnvelope(
            UUID.randomUUID(), "BookingPlatformCancelled", "Booking", bookingId.toString(), now,
            ObjectMapper().createObjectNode().put("refundAmountMinor", 15_000),
        )
        every { processed.claim("wallet-service", event.eventId, now) } returns true
        every { transactions.terminateBookingPayment(bookingId, event.eventId, 15_000) } returns UUID.randomUUID()

        handler.handle(event)

        verify(exactly = 1) { transactions.terminateBookingPayment(bookingId, event.eventId, 15_000) }
    }
}
