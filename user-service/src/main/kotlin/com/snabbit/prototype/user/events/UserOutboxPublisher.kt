package com.snabbit.prototype.user.events

import com.fasterxml.jackson.databind.ObjectMapper
import java.time.Clock
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.UUID
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class UserOutboxPublisher(
    private val store: UserOutboxDeliveryStore,
    private val kafka: KafkaTemplate<String, String>,
    private val objectMapper: ObjectMapper,
    private val properties: UserEventingProperties,
    private val clock: Clock,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${user-eventing.poll-delay}")
    fun publishAvailableEvents() {
        val claimId = UUID.randomUUID()
        val now = clock.instant()
        store.claimBatch(claimId, now, now.plus(properties.claimDuration), properties.batchSize)
            .forEach { publishOne(it, claimId) }
    }

    private fun publishOne(event: ClaimedOutboxEvent, claimId: UUID) {
        try {
            val envelope = EventEnvelope(
                event.id, event.eventType, event.aggregateType, event.aggregateId, event.occurredAt,
                objectMapper.readTree(event.payload), event.schemaVersion,
            )
            kafka.send(properties.topic, event.aggregateId, objectMapper.writeValueAsString(envelope))
                .get(10, TimeUnit.SECONDS)
            store.markPublished(event.id, claimId, clock.instant())
        } catch (error: Exception) {
            logger.warn("User outbox event {} could not be published", event.id, error)
            store.releaseAfterFailure(event.id, claimId, clock.instant().plus(Duration.ofSeconds(5)), error.message.orEmpty())
        }
    }
}
