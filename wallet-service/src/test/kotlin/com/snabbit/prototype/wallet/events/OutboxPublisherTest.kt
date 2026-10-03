package com.snabbit.prototype.wallet.events

import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.CompletableFuture
import java.util.UUID
import org.apache.kafka.clients.producer.ProducerRecord
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult

class OutboxPublisherTest {
    private val store = mockk<OutboxDeliveryStore>()
    private val kafka = mockk<KafkaTemplate<String, String>>()
    private val mapper = ObjectMapper().findAndRegisterModules()
    private val now = Instant.parse("2026-10-02T04:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val properties = EventingProperties(
        outboxTopic = "wallet-events",
        consumerTopic = "booking-events",
        consumerGroup = "wallet-service",
        outboxPollDelay = Duration.ofSeconds(1),
        outboxClaimDuration = Duration.ofSeconds(30),
        outboxBatchSize = 50,
    )
    private val publisher = OutboxPublisher(store, kafka, mapper, properties, clock)

    @Test
    fun `publishes the persisted schema version before acknowledging the outbox row`() {
        val event = ClaimedOutboxEvent(
            UUID.randomUUID(), "Wallet", UUID.randomUUID().toString(), "WalletPaymentSucceeded",
            "{}", now, 1,
        )
        val published = slot<String>()
        every { store.claimBatch(any(), now, now.plusSeconds(30), 50) } returns listOf(event)
        every { kafka.send("wallet-events", event.aggregateId, capture(published)) } returns
            CompletableFuture.completedFuture(
                SendResult(ProducerRecord("wallet-events", event.aggregateId, "value"), mockk()),
            )
        every { store.markPublished(event.id, any(), now) } returns Unit

        publisher.publishAvailableEvents()

        assertEquals(1, mapper.readTree(published.captured).path("schemaVersion").asInt())
        verify(exactly = 1) { store.markPublished(event.id, any(), now) }
        verify(exactly = 0) { store.releaseAfterFailure(any(), any(), any(), any()) }
    }
}
