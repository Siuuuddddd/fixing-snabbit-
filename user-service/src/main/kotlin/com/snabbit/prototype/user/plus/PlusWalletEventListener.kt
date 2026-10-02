package com.snabbit.prototype.user.plus

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.annotation.DltHandler
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.annotation.RetryableTopic
import org.springframework.retry.annotation.Backoff
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class PlusWalletEventHandler(
    private val processed: PlusProcessedMessageStore,
    private val plus: PlusService,
    @param:Value("\${plus-purchase-events.consumer-group:user-service-plus-purchases}")
    private val consumerGroup: String,
) {
    @Transactional
    fun handle(event: BookingEventEnvelope) {
        if (event.eventType != "PlusSubscriptionPurchased") return
        if (!processed.claim(consumerGroup, event.eventId, event.occurredAt)) return
        val payload = event.payload
        val purchaseId = UUID.fromString(payload.requiredText("purchaseId"))
        val customerId = UUID.fromString(payload.requiredText("customerId"))
        val durationDays = payload.requiredInt("durationDays")
        val passCount = payload.requiredInt("passCount")
        val passDiscountMinor = payload.requiredLong("passDiscountMinor")
        val serviceCode = payload.path("serviceCode").asText().takeIf(String::isNotBlank)
        plus.activatePurchase(
            customerId, purchaseId, event.occurredAt, durationDays,
            passCount, passDiscountMinor, serviceCode,
        )
    }

    private fun com.fasterxml.jackson.databind.JsonNode.requiredText(field: String): String =
        path(field).asText().takeIf(String::isNotBlank) ?: error("Plus purchase event is missing $field")

    private fun com.fasterxml.jackson.databind.JsonNode.requiredInt(field: String): Int =
        path(field).takeIf { it.canConvertToInt() }?.asInt() ?: error("Plus purchase event is missing $field")

    private fun com.fasterxml.jackson.databind.JsonNode.requiredLong(field: String): Long =
        path(field).takeIf { it.canConvertToLong() }?.asLong() ?: error("Plus purchase event is missing $field")
}

@Component
class PlusWalletEventListener(
    private val mapper: ObjectMapper,
    private val handler: PlusWalletEventHandler,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @RetryableTopic(
        attempts = "4",
        backoff = Backoff(delay = 1_000, multiplier = 2.0),
        retryTopicSuffix = "-retry",
        dltTopicSuffix = "-dlt",
    )
    @KafkaListener(
        topics = ["\${plus-purchase-events.consumer-topic:wallet-events}"],
        groupId = "\${plus-purchase-events.consumer-group:user-service-plus-purchases}",
    )
    fun consume(rawEvent: String) {
        handler.handle(mapper.readValue(rawEvent, BookingEventEnvelope::class.java))
    }

    @DltHandler
    fun deadLetter(rawEvent: String) {
        logger.error("Wallet event for Plus purchase moved to dead-letter topic: {}", rawEvent.take(500))
    }
}
