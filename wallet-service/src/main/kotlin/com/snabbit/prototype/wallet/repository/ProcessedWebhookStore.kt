package com.snabbit.prototype.wallet.repository

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
class ProcessedWebhookStore(private val jdbcTemplate: JdbcTemplate) {
    fun claim(provider: String, eventId: String, eventType: String, now: Instant): Boolean =
        jdbcTemplate.update(
            """
            INSERT INTO processed_webhook (provider, event_id, event_type, processed_at)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (provider, event_id) DO NOTHING
            """.trimIndent(),
            provider,
            eventId,
            eventType,
            now,
        ) == 1
}
