package com.snabbit.prototype.user.events

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Repository
class UserOutboxDeliveryStore(private val jdbc: JdbcTemplate) {
    fun claimBatch(claimId: UUID, now: Instant, claimUntil: Instant, limit: Int): List<ClaimedOutboxEvent> =
        jdbc.query(
            CLAIM_SQL,
            { result, _ ->
                ClaimedOutboxEvent(
                    result.getObject("id", UUID::class.java),
                    result.getString("aggregate_type"),
                    result.getString("aggregate_id"),
                    result.getString("event_type"),
                    result.getString("payload"),
                    result.getTimestamp("occurred_at").toInstant(),
                    result.getInt("schema_version"),
                )
            },
            Timestamp.from(now), limit, claimId, Timestamp.from(claimUntil),
        )

    fun markPublished(eventId: UUID, claimId: UUID, now: Instant) {
        jdbc.update(
            """
            UPDATE outbox_event
            SET published_at = ?, claimed_by = NULL, claim_until = NULL, last_error = NULL
            WHERE id = ? AND claimed_by = ? AND published_at IS NULL
            """.trimIndent(),
            Timestamp.from(now), eventId, claimId,
        )
    }

    fun releaseAfterFailure(eventId: UUID, claimId: UUID, retryAt: Instant, error: String) {
        jdbc.update(
            """
            UPDATE outbox_event
            SET claimed_by = NULL, claim_until = ?, last_error = ?
            WHERE id = ? AND claimed_by = ? AND published_at IS NULL
            """.trimIndent(),
            Timestamp.from(retryAt), error.take(500), eventId, claimId,
        )
    }

    private companion object {
        val CLAIM_SQL = """
            WITH candidates AS (
                SELECT id FROM outbox_event
                WHERE published_at IS NULL AND (claim_until IS NULL OR claim_until <= ?)
                ORDER BY occurred_at, id FOR UPDATE SKIP LOCKED LIMIT ?
            )
            UPDATE outbox_event AS event
            SET claimed_by = ?, claim_until = ?, publish_attempts = publish_attempts + 1, last_error = NULL
            FROM candidates WHERE event.id = candidates.id
            RETURNING event.id, event.aggregate_type, event.aggregate_id, event.event_type,
                      event.payload::text, event.occurred_at, event.schema_version
        """.trimIndent()
    }
}
