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
