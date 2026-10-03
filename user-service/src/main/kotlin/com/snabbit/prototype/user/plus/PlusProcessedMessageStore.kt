package com.snabbit.prototype.user.plus

import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

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
