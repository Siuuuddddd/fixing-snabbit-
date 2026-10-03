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

const val CURRENT_EVENT_SCHEMA_VERSION = 1

data class BookingEventEnvelope(
    val eventId: UUID,
    val eventType: String,
    val aggregateType: String,
    val aggregateId: String,
    val occurredAt: Instant,
    val payload: JsonNode,
    val schemaVersion: Int = CURRENT_EVENT_SCHEMA_VERSION,
) {
    init {
        require(schemaVersion == CURRENT_EVENT_SCHEMA_VERSION) {
            "Unsupported event schema version $schemaVersion"
        }
    }
}
