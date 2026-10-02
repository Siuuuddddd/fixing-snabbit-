package com.snabbit.prototype.booking.events

import com.fasterxml.jackson.databind.ObjectMapper
import com.snabbit.prototype.booking.service.BookingLifecycleService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class WalletEventHandlerTest {
    private val processed = mockk<ProcessedMessageStore>()
    private val bookingService = mockk<BookingLifecycleService>()
    private val now = Instant.parse("2026-10-02T04:00:00Z")
    private val properties = EventingProperties(
        "booking-events",
        "wallet-events",
        "booking-service",
        Duration.ofSeconds(1),
        Duration.ofSeconds(30),
        50,
    )
    private val handler = WalletEventHandler(processed, bookingService, properties, Clock.fixed(now, ZoneOffset.UTC))

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
    }
}
