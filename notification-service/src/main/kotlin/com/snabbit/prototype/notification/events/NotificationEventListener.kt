package com.snabbit.prototype.notification.events

import com.snabbit.prototype.notification.dto.response.NotificationTemplate

import com.snabbit.prototype.notification.domain.enums.RecipientType

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.snabbit.prototype.notification.service.NotificationProperties
import com.snabbit.prototype.notification.service.NotificationTransaction
import com.snabbit.prototype.notification.security.InternalServiceClientFactory
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
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
import java.time.Clock
import java.time.Instant
import java.util.UUID

@Component
class NotificationEventListener(
    private val mapper: ObjectMapper,
    private val resolver: EventRecipientResolver,
    private val templates: NotificationTemplates,
    private val handler: NotificationEventHandler,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @RetryableTopic(
        attempts = "4",
        backoff = Backoff(delay = 1_000, multiplier = 2.0),
        retryTopicSuffix = "-retry",
        dltTopicSuffix = "-dlt",
    )
    @KafkaListener(
        topics = ["\${notifications.booking-topic}", "\${notifications.wallet-topic}"],
        groupId = "\${notifications.consumer-group}",
    )
    fun consume(rawEvent: String) {
        val event = mapper.readValue(rawEvent, EventEnvelope::class.java)
        val template = templates.forEvent(event) ?: return
        if (event.eventType == "WorkerOffersCreated") {
            fanOutWorkerOffers(event, template)
        } else {
            handler.handle(
                event,
                RecipientType.CUSTOMER,
                resolver.customerId(event),
                template,
            )
        }
    }

    private fun fanOutWorkerOffers(event: EventEnvelope, template: NotificationTemplate) {
        val offersByWorker = event.payload.path("offers")
            .filter { it.path("workerId").asText().isNotBlank() }
            .groupBy { UUID.fromString(it.path("workerId").asText()) }
        offersByWorker.forEach { (workerId, offers) ->
            val recipientEventId = UUID.nameUUIDFromBytes("${event.eventId}:$workerId".toByteArray(Charsets.UTF_8))
            val recipientPayload = mapper.createObjectNode().apply {
                set<com.fasterxml.jackson.databind.JsonNode>("bookingId", event.payload.path("bookingId"))
                set<com.fasterxml.jackson.databind.JsonNode>("offers", mapper.valueToTree(offers))
            }
            handler.handle(
                event.copy(eventId = recipientEventId, payload = recipientPayload),
                RecipientType.WORKER,
                workerId,
                template,
            )
        }
    }

    @DltHandler
    fun deadLetter(rawEvent: String) {
        logger.error("Notification event moved to dead-letter topic: {}", rawEvent.take(500))
    }
}
