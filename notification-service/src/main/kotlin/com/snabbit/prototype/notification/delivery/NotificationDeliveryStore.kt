package com.snabbit.prototype.notification.delivery

import com.snabbit.prototype.notification.domain.enums.RecipientType
import com.snabbit.prototype.notification.dto.response.ClaimedDelivery
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.util.UUID
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class NotificationDeliveryStore(private val jdbc: JdbcTemplate) {
    fun claimBatch(claimId: UUID, now: Instant, claimUntil: Instant, limit: Int): List<ClaimedDelivery> =
        jdbc.query(
            """
            WITH candidates AS (
                SELECT delivery.id
                FROM notification_delivery delivery
                WHERE (
                    (delivery.status = 'PENDING' AND delivery.next_attempt_at <= ?)
                    OR (delivery.status = 'PROCESSING' AND delivery.claim_until < ?)
                )
                ORDER BY delivery.next_attempt_at, delivery.id
                FOR UPDATE SKIP LOCKED
                LIMIT ?
            )
            UPDATE notification_delivery delivery
            SET status = 'PROCESSING', claimed_by = ?, claim_until = ?, attempts = delivery.attempts + 1
            FROM candidates, notification notification
            WHERE delivery.id = candidates.id AND notification.id = delivery.notification_id
            RETURNING delivery.id, delivery.notification_id, notification.recipient_type, notification.recipient_id,
                      delivery.channel, notification.event_type, notification.title, notification.body,
                      notification.payload::text AS payload, delivery.attempts
            """.trimIndent(),
            { result, _ ->
                ClaimedDelivery(
                    UUID.fromString(result.getString("id")),
                    result.getLong("notification_id"),
                    RecipientType.valueOf(result.getString("recipient_type")),
                    UUID.fromString(result.getString("recipient_id")),
                    result.getString("channel"),
                    result.getString("event_type"),
                    result.getString("title"),
                    result.getString("body"),
                    result.getString("payload"),
                    result.getInt("attempts"),
                )
            },
            Timestamp.from(now), Timestamp.from(now), limit, claimId, Timestamp.from(claimUntil),
        )

    fun markDelivered(deliveryId: UUID, claimId: UUID, now: Instant) {
        jdbc.update(
            """
            UPDATE notification_delivery
            SET status = 'DELIVERED', delivered_at = ?, claimed_by = NULL, claim_until = NULL, last_error = NULL
            WHERE id = ? AND claimed_by = ? AND status = 'PROCESSING'
            """.trimIndent(),
            Timestamp.from(now), deliveryId, claimId,
        )
    }

    fun releaseFailure(
        deliveryId: UUID,
        claimId: UUID,
        attempts: Int,
        maxAttempts: Int,
        now: Instant,
        error: String,
    ) {
        val dead = attempts >= maxAttempts
        val retryDelay = Duration.ofSeconds((30L shl (attempts - 1).coerceIn(0, 6)))
        jdbc.update(
            """
            UPDATE notification_delivery
            SET status = ?, next_attempt_at = ?, claimed_by = NULL, claim_until = NULL, last_error = ?
            WHERE id = ? AND claimed_by = ? AND status = 'PROCESSING'
            """.trimIndent(),
            if (dead) "DEAD" else "PENDING",
            Timestamp.from(now.plus(retryDelay)),
            error.take(500),
            deliveryId,
            claimId,
        )
    }
}
