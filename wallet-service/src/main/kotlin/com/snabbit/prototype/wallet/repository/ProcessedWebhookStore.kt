package com.snabbit.prototype.wallet.repository

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.time.Instant

data class StoredWebhookCapture(
    val eventId: String,
    val eventType: String,
    val providerOrderId: String,
    val providerPaymentId: String,
    val amountMinor: Long,
)

@Repository
class ProcessedWebhookStore(private val jdbcTemplate: JdbcTemplate) {
    fun claim(
        provider: String,
        eventId: String,
        eventType: String,
        providerOrderId: String,
        providerPaymentId: String,
        amountMinor: Long,
        now: Instant,
    ): Boolean {
        jdbcTemplate.update(
            """
            INSERT INTO processed_webhook (
                provider, event_id, event_type, processed_at, provider_order_id,
                provider_payment_id, amount_minor, processing_status, attempt_count, last_attempt_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, 'PENDING', 0, NULL)
            ON CONFLICT (provider, event_id) DO NOTHING
            """.trimIndent(),
            provider,
            eventId,
            eventType,
            now,
            providerOrderId,
            providerPaymentId,
            amountMinor,
        )
        val stored = jdbcTemplate.queryForObject(
            """
            SELECT event_type, provider_order_id, provider_payment_id, amount_minor, processing_status
            FROM processed_webhook
            WHERE provider = ? AND event_id = ?
            FOR UPDATE
            """.trimIndent(),
            { result, _ ->
                StoredClaim(
                    eventType = result.getString("event_type"),
                    providerOrderId = result.getString("provider_order_id"),
                    providerPaymentId = result.getString("provider_payment_id"),
                    amountMinor = result.getObject("amount_minor", java.lang.Long::class.java)?.toLong(),
                    status = result.getString("processing_status"),
                )
            },
            provider,
            eventId,
        ) ?: error("Stored webhook disappeared while being claimed")
        val captureMatches = stored.eventType == eventType &&
            (stored.providerOrderId == null || stored.providerOrderId == providerOrderId) &&
            (stored.providerPaymentId == null || stored.providerPaymentId == providerPaymentId) &&
            (stored.amountMinor == null || stored.amountMinor == amountMinor)
        check(captureMatches) { "Webhook event ID was reused with different capture details" }
        if (stored.status == "PROCESSED") return false
        jdbcTemplate.update(
            """
            UPDATE processed_webhook
            SET attempt_count = attempt_count + 1, last_attempt_at = ?
            WHERE provider = ? AND event_id = ?
            """.trimIndent(),
            now,
            provider,
            eventId,
        )
        return true
    }

    fun complete(provider: String, eventId: String, now: Instant) {
        val changed = jdbcTemplate.update(
            """
            UPDATE processed_webhook
            SET processing_status = 'PROCESSED', completed_at = ?
            WHERE provider = ? AND event_id = ? AND processing_status = 'PENDING'
            """.trimIndent(),
            now,
            provider,
            eventId,
        )
        check(changed == 1) { "Pending webhook could not be completed" }
    }

    fun pendingCaptures(provider: String, limit: Int): List<StoredWebhookCapture> = jdbcTemplate.query(
        """
        SELECT event_id, event_type, provider_order_id, provider_payment_id, amount_minor
        FROM processed_webhook
        WHERE provider = ? AND processing_status = 'PENDING'
        ORDER BY last_attempt_at ASC NULLS FIRST, processed_at, event_id
        LIMIT ?
        """.trimIndent(),
        { result, _ ->
            StoredWebhookCapture(
                eventId = result.getString("event_id"),
                eventType = result.getString("event_type"),
                providerOrderId = result.getString("provider_order_id"),
                providerPaymentId = result.getString("provider_payment_id"),
                amountMinor = result.getLong("amount_minor"),
            )
        },
        provider,
        limit,
    )

    private data class StoredClaim(
        val eventType: String,
        val providerOrderId: String?,
        val providerPaymentId: String?,
        val amountMinor: Long?,
        val status: String,
    )
}
