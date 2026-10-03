package com.snabbit.prototype.notification.events

import com.fasterxml.jackson.databind.ObjectMapper
import com.snabbit.prototype.notification.domain.enums.RecipientType
import com.snabbit.prototype.notification.dto.response.NotificationTemplate
import com.snabbit.prototype.notification.service.NotificationProperties
import com.snabbit.prototype.notification.service.NotificationTransaction
import java.time.Clock
import java.time.Instant
import java.util.UUID
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

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
