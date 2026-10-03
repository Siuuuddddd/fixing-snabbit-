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

@Service
class NotificationEventHandler(
    private val processed: ProcessedEventStore,
    private val transactions: NotificationTransaction,
    private val properties: NotificationProperties,
    private val mapper: ObjectMapper,
    private val clock: Clock,
) {
    @Transactional
    fun handle(
        event: EventEnvelope,
        recipientType: RecipientType,
        recipientId: UUID,
        template: NotificationTemplate,
    ) {
        if (!processed.claim(properties.consumerGroup, event.eventId, clock.instant())) return
        transactions.record(
            event.eventId,
            recipientType,
            recipientId,
            event.eventType,
            template,
            mapper.writeValueAsString(event.payload),
        )
    }
}
