package com.snabbit.prototype.booking.eventing

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.DltHandler
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.annotation.RetryableTopic
import org.springframework.retry.annotation.Backoff
import org.springframework.stereotype.Component

@Component
class WalletEventListener(
    private val objectMapper: ObjectMapper,
    private val handler: WalletEventHandler,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @RetryableTopic(
        attempts = "4",
        backoff = Backoff(delay = 1_000, multiplier = 2.0),
        retryTopicSuffix = "-retry",
        dltTopicSuffix = "-dlt",
    )
    @KafkaListener(topics = ["\${eventing.consumer-topic}"], groupId = "\${eventing.consumer-group}")
    fun consume(rawEvent: String) {
        handler.handle(objectMapper.readValue(rawEvent, EventEnvelope::class.java))
    }

    @DltHandler
    fun deadLetter(rawEvent: String) {
        logger.error("Wallet event moved to dead-letter topic: {}", rawEvent.take(500))
    }
}
