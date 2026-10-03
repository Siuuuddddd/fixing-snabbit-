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
