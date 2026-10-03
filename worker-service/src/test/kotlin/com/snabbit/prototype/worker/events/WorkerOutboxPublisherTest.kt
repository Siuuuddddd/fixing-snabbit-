package com.snabbit.prototype.worker.events

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

class WorkerOutboxPublisherTest {
    @Test
    fun `marks the leased worker event only after Kafka acknowledgement`() {
        val now = Instant.parse("2026-10-03T04:00:00Z")
        val store = mockk<WorkerOutboxDeliveryStore>()
        val kafka = mockk<KafkaTemplate<String, String>>()
        val event = ClaimedOutboxEvent(
            UUID.randomUUID(), "Worker", UUID.randomUUID().toString(), "WorkerAvailabilityUpdated",
            "{}", now, 1,
        )
        every { store.claimBatch(any(), now, now.plusSeconds(30), 50) } returns listOf(event)
        every { kafka.send("worker-events", event.aggregateId, any()) } returns CompletableFuture.completedFuture(
            SendResult(ProducerRecord("worker-events", event.aggregateId, "value"), mockk()),
        )
        every { store.markPublished(event.id, any(), now) } returns Unit
        val publisher = WorkerOutboxPublisher(
            store, kafka, ObjectMapper().findAndRegisterModules(),
            WorkerEventingProperties("worker-events", Duration.ofSeconds(1), Duration.ofSeconds(30), 50),
            Clock.fixed(now, ZoneOffset.UTC),
        )

        publisher.publishAvailableEvents()

        verify(exactly = 1) { store.markPublished(event.id, any(), now) }
        verify(exactly = 0) { store.releaseAfterFailure(any(), any(), any(), any()) }
    }
}
