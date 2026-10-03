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
