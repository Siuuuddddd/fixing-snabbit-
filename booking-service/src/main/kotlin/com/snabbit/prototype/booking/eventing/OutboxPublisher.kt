package com.snabbit.prototype.booking.eventing

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.util.UUID
import java.util.concurrent.TimeUnit

@Component
@ConditionalOnProperty(prefix = "eventing", name = ["outbox-enabled"], havingValue = "true", matchIfMissing = true)
class OutboxPublisher(
    private val store: OutboxDeliveryStore,
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val objectMapper: ObjectMapper,
    private val properties: EventingProperties,
    private val clock: Clock,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${eventing.outbox-poll-delay}")
    fun publishAvailableEvents() {
        val claimId = UUID.randomUUID()
        val now = clock.instant()
        store.claimBatch(
            claimId,
            now,
            now.plus(properties.outboxClaimDuration),
            properties.outboxBatchSize,
        ).forEach { event -> publishOne(event, claimId) }
    }

    private fun publishOne(event: ClaimedOutboxEvent, claimId: UUID) {
        try {
            val envelope = EventEnvelope(
                eventId = event.id,
                eventType = event.eventType,
                aggregateType = event.aggregateType,
                aggregateId = event.aggregateId,
                occurredAt = event.occurredAt,
                payload = objectMapper.readTree(event.payload),
            )
            kafkaTemplate.send(
                properties.outboxTopic,
                event.aggregateId,
                objectMapper.writeValueAsString(envelope),
            ).get(10, TimeUnit.SECONDS)
            store.markPublished(event.id, claimId, clock.instant())
        } catch (error: Exception) {
            logger.warn("Outbox event {} could not be published", event.id, error)
            store.releaseAfterFailure(
                event.id,
                claimId,
                clock.instant().plus(Duration.ofSeconds(5)),
                error.message.orEmpty(),
            )
        }
    }
}
