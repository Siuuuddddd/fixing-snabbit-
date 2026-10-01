package com.snabbit.prototype.wallet.eventing

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Repository
class ProcessedMessageStore(private val jdbcTemplate: JdbcTemplate) {
    fun claim(consumerName: String, messageId: UUID, now: Instant): Boolean =
        jdbcTemplate.update(
            """
            INSERT INTO processed_message (consumer_name, message_id, processed_at)
            VALUES (?, ?, ?)
            ON CONFLICT (consumer_name, message_id) DO NOTHING
            """.trimIndent(),
            consumerName,
            messageId,
            Timestamp.from(now),
        ) == 1
}
