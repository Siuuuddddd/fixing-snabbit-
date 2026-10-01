package com.snabbit.prototype.booking.eventing

import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.apache.kafka.clients.producer.ProducerRecord
import org.junit.jupiter.api.Test
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.CompletableFuture

class OutboxPublisherTest {
    private val store = mockk<OutboxDeliveryStore>()
    private val kafka = mockk<KafkaTemplate<String, String>>()
    private val now = Instant.parse("2026-10-02T04:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val properties = EventingProperties(
        outboxTopic = "booking-events",
        consumerTopic = "wallet-events",
        consumerGroup = "booking-service",
        outboxPollDelay = Duration.ofSeconds(1),
        outboxClaimDuration = Duration.ofSeconds(30),
        outboxBatchSize = 50,
    )
    private val publisher = OutboxPublisher(store, kafka, ObjectMapper().findAndRegisterModules(), properties, clock)

    @Test
    fun `marks event published only after Kafka acknowledges it`() {
        val event = event()
        every { store.claimBatch(any(), now, now.plusSeconds(30), 50) } returns listOf(event)
        every { kafka.send("booking-events", event.aggregateId, any()) } returns CompletableFuture.completedFuture(
            SendResult(ProducerRecord("booking-events", event.aggregateId, "value"), mockk()),
        )
        every { store.markPublished(event.id, any(), now) } returns Unit

        publisher.publishAvailableEvents()

        verify(exactly = 1) { store.markPublished(event.id, any(), now) }
        verify(exactly = 0) { store.releaseAfterFailure(any(), any(), any(), any()) }
    }

    @Test
    fun `releases lease after Kafka failure`() {
        val event = event()
        val failed = CompletableFuture<SendResult<String, String>>()
        failed.completeExceptionally(IllegalStateException("Kafka unavailable"))
        every { store.claimBatch(any(), now, now.plusSeconds(30), 50) } returns listOf(event)
        every { kafka.send("booking-events", event.aggregateId, any()) } returns failed
        every { store.releaseAfterFailure(event.id, any(), now.plusSeconds(5), any()) } returns Unit

        publisher.publishAvailableEvents()

        verify(exactly = 0) { store.markPublished(any(), any(), any()) }
        verify(exactly = 1) { store.releaseAfterFailure(event.id, any(), now.plusSeconds(5), any()) }
    }

    private fun event() = ClaimedOutboxEvent(
        id = UUID.randomUUID(),
        aggregateType = "Booking",
        aggregateId = UUID.randomUUID().toString(),
        eventType = "BookingConfirmed",
        payload = "{\"bookingId\":\"${UUID.randomUUID()}\"}",
        occurredAt = now,
    )
}
