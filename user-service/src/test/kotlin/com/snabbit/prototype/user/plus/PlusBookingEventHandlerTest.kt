package com.snabbit.prototype.user.plus

import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class PlusBookingEventHandlerTest {
    private val processed = mockk<PlusProcessedMessageStore>()
    private val plus = mockk<PlusService>(relaxed = true)
    private val handler = PlusBookingEventHandler(processed, plus, "user-service-plus")
    private val mapper = ObjectMapper()

    @Test
    fun `confirmation consumes pass once`() {
        val event = event("BookingConfirmed")
        every { processed.claim("user-service-plus", event.eventId, event.occurredAt) } returns true andThen false

        handler.handle(event)
        handler.handle(event)

        verify(exactly = 1) { plus.consumePass(UUID.fromString(event.aggregateId), event.occurredAt) }
    }

    @Test
    fun `terminal pre-service booking facts release pass`() {
        listOf("BookingCancelled", "BookingExpired", "WorkerOfferRejected", "WorkerOfferExpired").forEach { eventType ->
            val event = event(eventType)
            every { processed.claim("user-service-plus", event.eventId, event.occurredAt) } returns true
            handler.handle(event)
            verify(exactly = 1) { plus.releasePass(UUID.fromString(event.aggregateId)) }
        }
    }

    @Test
    fun `confirmed booking cancellations restore a consumed pass`() {
        listOf("BookingCustomerCancelled", "BookingPlatformCancelled").forEach { eventType ->
            val event = event(eventType)
            every { processed.claim("user-service-plus", event.eventId, event.occurredAt) } returns true

            handler.handle(event)

            verify(exactly = 1) { plus.restorePass(UUID.fromString(event.aggregateId)) }
        }
    }

    private fun event(type: String): BookingEventEnvelope {
        val bookingId = UUID.randomUUID()
        return BookingEventEnvelope(
            UUID.randomUUID(), type, "Booking", bookingId.toString(),
            Instant.parse("2026-10-02T04:00:00Z"), mapper.createObjectNode(),
        )
    }
}
