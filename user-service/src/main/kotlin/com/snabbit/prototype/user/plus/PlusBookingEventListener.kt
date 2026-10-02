package com.snabbit.prototype.user.plus

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.kafka.annotation.DltHandler
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.annotation.RetryableTopic
import org.springframework.retry.annotation.Backoff
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

data class BookingEventEnvelope(
    val eventId: UUID,
    val eventType: String,
    val aggregateType: String,
    val aggregateId: String,
    val occurredAt: Instant,
    val payload: JsonNode,
)

@Repository
class PlusProcessedMessageStore(private val jdbc: JdbcTemplate) {
    fun claim(consumer: String, eventId: UUID, now: Instant): Boolean = jdbc.update(
        """
        INSERT INTO processed_message (consumer_name, message_id, processed_at)
        VALUES (?, ?, ?)
        ON CONFLICT (consumer_name, message_id) DO NOTHING
        """.trimIndent(),
        consumer, eventId, Timestamp.from(now),
    ) == 1
}

@Service
class PlusBookingEventHandler(
    private val processed: PlusProcessedMessageStore,
    private val plus: PlusService,
    @param:Value("\${plus-events.consumer-group:user-service-plus}") private val consumerGroup: String,
) {
    @Transactional
    fun handle(event: BookingEventEnvelope) {
        if (!processed.claim(consumerGroup, event.eventId, event.occurredAt)) return
        val bookingId = UUID.fromString(event.aggregateId)
        when (event.eventType) {
            "BookingConfirmed" -> plus.consumePass(bookingId, event.occurredAt)
            "BookingCustomerCancelled", "BookingPlatformCancelled" -> plus.restorePass(bookingId)
            "BookingCancelled", "BookingExpired", "WorkerOfferRejected", "WorkerOfferExpired" ->
                plus.releasePass(bookingId)
        }
    }
}

@Component
class PlusBookingEventListener(
    private val mapper: ObjectMapper,
    private val handler: PlusBookingEventHandler,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @RetryableTopic(
        attempts = "4",
        backoff = Backoff(delay = 1_000, multiplier = 2.0),
        retryTopicSuffix = "-retry",
        dltTopicSuffix = "-dlt",
    )
    @KafkaListener(topics = ["\${plus-events.consumer-topic:booking-events}"], groupId = "\${plus-events.consumer-group:user-service-plus}")
    fun consume(rawEvent: String) {
        handler.handle(mapper.readValue(rawEvent, BookingEventEnvelope::class.java))
    }

    @DltHandler
    fun deadLetter(rawEvent: String) {
        logger.error("Booking event for Plus entitlements moved to dead-letter topic: {}", rawEvent.take(500))
    }
}
