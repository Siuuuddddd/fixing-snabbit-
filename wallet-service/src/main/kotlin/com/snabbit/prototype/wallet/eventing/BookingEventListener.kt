package com.snabbit.prototype.wallet.eventing

import com.fasterxml.jackson.databind.ObjectMapper
import com.snabbit.prototype.wallet.application.WalletService
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.DltHandler
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.annotation.RetryableTopic
import org.springframework.retry.annotation.Backoff
import org.springframework.stereotype.Component

@Component
class BookingEventListener(
    private val objectMapper: ObjectMapper,
    private val handler: BookingEventHandler,
    private val walletService: WalletService,
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
        val refundId = handler.handle(objectMapper.readValue(rawEvent, EventEnvelope::class.java))
        refundId?.let { id ->
            runCatching { walletService.executeRefund(id) }
                .onFailure { error -> logger.warn("Refund {} remains queued after booking event", id, error) }
        }
    }

    @DltHandler
    fun deadLetter(rawEvent: String) {
        logger.error("Booking event moved to dead-letter topic: {}", rawEvent.take(500))
    }
}
