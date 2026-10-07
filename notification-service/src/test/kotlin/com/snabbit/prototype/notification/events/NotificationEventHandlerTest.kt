package com.snabbit.prototype.notification.events

import com.fasterxml.jackson.databind.ObjectMapper
import com.snabbit.prototype.notification.domain.enums.RecipientType
import com.snabbit.prototype.notification.dto.response.NotificationTemplate
import com.snabbit.prototype.notification.service.NotificationProperties
import com.snabbit.prototype.notification.service.NotificationTransaction
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NotificationEventHandlerTest {
    private val mapper = ObjectMapper().findAndRegisterModules()
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

        handler.handle(event, RecipientType.CUSTOMER, customerId, template)
        handler.handle(event, RecipientType.CUSTOMER, customerId, template)

        verify(exactly = 1) {
            transactions.record(event.eventId, RecipientType.CUSTOMER, customerId, event.eventType, template, any())
        }
    }

    @Test
    fun `template catalog marks cancellation and refund as critical`() {
        val templates = NotificationTemplates()

        assertTrue(templates.forEvent(event("BookingPlatformCancelled"))!!.critical)
        assertTrue(templates.forEvent(event("WalletRefundCompleted"))!!.critical)
        assertTrue(templates.forEvent(event("PlusSubscriptionPurchased"))!!.critical)
        assertTrue(templates.forEvent(event("WorkerOffersCreated"))!!.critical)
        assertEquals(null, templates.forEvent(event("UnknownEvent")))
    }

    @Test
    fun `worker offer event fans out one privacy-filtered notification per worker`() {
        val resolver = mockk<EventRecipientResolver>()
        val workerRecipients = mockk<WorkerPushRecipientResolver>()
        val fanoutHandler = mockk<NotificationEventHandler>(relaxed = true)
        val listener = NotificationEventListener(
            mapper, resolver, workerRecipients, NotificationTemplates(), fanoutHandler,
        )
        val firstWorker = UUID.randomUUID()
        val secondWorker = UUID.randomUUID()
        val bookingId = UUID.randomUUID()
        val payload = mapper.createObjectNode().apply {
            put("bookingId", bookingId.toString())
            set<com.fasterxml.jackson.databind.JsonNode>(
                "offers",
                mapper.valueToTree(
                    listOf(
                        mapOf("offerId" to UUID.randomUUID(), "workerId" to firstWorker),
                        mapOf("offerId" to UUID.randomUUID(), "workerId" to firstWorker),
                        mapOf("offerId" to UUID.randomUUID(), "workerId" to secondWorker),
                    ),
                ),
            )
        }
        val event = EventEnvelope(UUID.randomUUID(), "WorkerOffersCreated", "Booking", bookingId.toString(), now, payload)
        val capturedEvents = mutableListOf<EventEnvelope>()
        val capturedWorkers = mutableListOf<UUID>()

        listener.consume(mapper.writeValueAsString(event))

        verify(exactly = 2) {
            fanoutHandler.handle(
                capture(capturedEvents), RecipientType.WORKER, capture(capturedWorkers), any(),
            )
        }
        capturedEvents.zip(capturedWorkers).forEach { (workerEvent, workerId) ->
            assertTrue(workerEvent.payload.path("offers").all { it.path("workerId").asText() == workerId.toString() })
        }
        assertEquals(setOf(firstWorker, secondWorker), capturedWorkers.toSet())
        verify(exactly = 0) { resolver.customerId(any()) }
    }

    @Test
    fun `worker lifecycle events deliver expiry assignment and cancellation`() {
        val resolver = mockk<EventRecipientResolver>()
        val workerRecipients = mockk<WorkerPushRecipientResolver>()
        val fanoutHandler = mockk<NotificationEventHandler>(relaxed = true)
        val listener = NotificationEventListener(
            mapper, resolver, workerRecipients, NotificationTemplates(), fanoutHandler,
        )
        val workerId = UUID.randomUUID()
        val customerId = UUID.randomUUID()
        val bookingId = UUID.randomUUID()
        every { resolver.customerId(any()) } returns customerId
        val storedJob = mapper.createObjectNode().apply {
            put("workerId", workerId.toString())
            put("jobId", UUID.randomUUID().toString())
            put("visitIndex", 0)
        }
        every { workerRecipients.targets(bookingId) } returns listOf(WorkerPushTarget(workerId, listOf(storedJob)))

        val expiry = eventWithEntries(
            "WorkerOfferExpired", bookingId, "offers", workerId,
            mapOf("offerId" to UUID.randomUUID(), "jobId" to UUID.randomUUID(), "visitIndex" to 0),
        )
        val assigned = eventWithEntries(
            "BookingConfirmed", bookingId, "jobs", workerId,
            mapOf("jobId" to UUID.randomUUID(), "visitIndex" to 0),
        )
        val cancelled = eventWithEntries(
            "BookingCustomerCancelled", bookingId, "jobs", workerId,
            mapOf("jobId" to UUID.randomUUID(), "visitIndex" to 0),
        )

        listOf(expiry, assigned, cancelled).forEach { listener.consume(mapper.writeValueAsString(it)) }

        listOf(expiry, assigned, cancelled).forEach { source ->
            verify(exactly = 1) {
                fanoutHandler.handle(source, RecipientType.CUSTOMER, customerId, any())
            }
            verify(exactly = 1) {
                fanoutHandler.handle(
                    match { it.eventType == source.eventType && it.eventId != source.eventId },
                    RecipientType.WORKER,
                    workerId,
                    any(),
                )
            }
        }
    }

    private fun eventWithEntries(
        type: String,
        bookingId: UUID,
        field: String,
        workerId: UUID,
        values: Map<String, Any>,
    ): EventEnvelope {
        val entry = values + ("workerId" to workerId)
        val payload = mapper.createObjectNode().apply {
            put("bookingId", bookingId.toString())
            set<com.fasterxml.jackson.databind.JsonNode>(field, mapper.valueToTree(listOf(entry)))
        }
        return EventEnvelope(UUID.randomUUID(), type, "Booking", bookingId.toString(), now, payload)
    }

    private fun event(type: String) = EventEnvelope(
        UUID.randomUUID(), type, "Booking", UUID.randomUUID().toString(), now,
        mapper.createObjectNode().put("bookingId", UUID.randomUUID().toString()),
    )
}
