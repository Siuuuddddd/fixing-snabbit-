package com.snabbit.prototype.notification.eventing

import com.fasterxml.jackson.databind.ObjectMapper
import com.snabbit.prototype.notification.application.NotificationProperties
import com.snabbit.prototype.notification.application.NotificationTemplate
import com.snabbit.prototype.notification.application.NotificationTransaction
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class NotificationEventHandlerTest {
    private val mapper = ObjectMapper()
    private val processed = mockk<ProcessedEventStore>()
    private val transactions = mockk<NotificationTransaction>(relaxed = true)
    private val properties = NotificationProperties(
        "notification-service", "booking-events", "wallet-events", Duration.ofSeconds(1),
        Duration.ofSeconds(5), Duration.ofSeconds(30), 50, 5,
    )
    private val now = Instant.parse("2026-10-02T04:00:00Z")
    private val handler = NotificationEventHandler(
        processed, transactions, properties, mapper, Clock.fixed(now, ZoneOffset.UTC),
    )

    @Test
    fun `duplicate Kafka event creates one notification`() {
        val event = event("BookingConfirmed")
        val customerId = UUID.randomUUID()
        val template = NotificationTemplate("Confirmed", "Ready", true)
        every { processed.claim("notification-service", event.eventId, now) } returns true andThen false

        handler.handle(event, customerId, template)
        handler.handle(event, customerId, template)

        verify(exactly = 1) { transactions.record(event.eventId, customerId, event.eventType, template, any()) }
    }

    @Test
    fun `template catalog marks cancellation and refund as critical`() {
        val templates = NotificationTemplates()

        assertTrue(templates.forEvent(event("BookingPlatformCancelled"))!!.critical)
        assertTrue(templates.forEvent(event("WalletRefundCompleted"))!!.critical)
        assertEquals(null, templates.forEvent(event("UnknownEvent")))
    }

    private fun event(type: String) = EventEnvelope(
        UUID.randomUUID(), type, "Booking", UUID.randomUUID().toString(), now,
        mapper.createObjectNode().put("bookingId", UUID.randomUUID().toString()),
    )
}
