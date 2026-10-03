package com.snabbit.prototype.user.events

import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.CompletableFuture
import java.util.UUID
import org.apache.kafka.clients.producer.ProducerRecord
import org.junit.jupiter.api.Test
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult

class UserOutboxPublisherTest {
    @Test
    fun `marks the leased user event only after Kafka acknowledgement`() {
        val now = Instant.parse("2026-10-03T04:00:00Z")
        val store = mockk<UserOutboxDeliveryStore>()
        val kafka = mockk<KafkaTemplate<String, String>>()
        val event = ClaimedOutboxEvent(
            UUID.randomUUID(), "User", UUID.randomUUID().toString(), "HouseholdPreferencesUpdated",
            "{}", now, 1,
        )
        every { store.claimBatch(any(), now, now.plusSeconds(30), 50) } returns listOf(event)
        every { kafka.send("user-events", event.aggregateId, any()) } returns CompletableFuture.completedFuture(
            SendResult(ProducerRecord("user-events", event.aggregateId, "value"), mockk()),
        )
        every { store.markPublished(event.id, any(), now) } returns Unit
        val publisher = UserOutboxPublisher(
            store, kafka, ObjectMapper().findAndRegisterModules(),
            UserEventingProperties("user-events", Duration.ofSeconds(1), Duration.ofSeconds(30), 50),
            Clock.fixed(now, ZoneOffset.UTC),
        )

        publisher.publishAvailableEvents()

        verify(exactly = 1) { store.markPublished(event.id, any(), now) }
        verify(exactly = 0) { store.releaseAfterFailure(any(), any(), any(), any()) }
    }
}
