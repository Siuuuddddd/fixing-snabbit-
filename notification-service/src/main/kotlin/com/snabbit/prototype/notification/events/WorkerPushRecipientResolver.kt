package com.snabbit.prototype.notification.events

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.util.UUID
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

data class WorkerPushTarget(val workerId: UUID, val jobs: List<JsonNode>)

@Repository
class WorkerPushRecipientResolver(
    private val jdbc: JdbcTemplate,
    private val mapper: ObjectMapper,
) {
    fun targets(bookingId: UUID): List<WorkerPushTarget> = jdbc.query(
        """
        SELECT recipient_id, payload::text AS payload
        FROM notification
        WHERE recipient_type = 'WORKER'
          AND event_type = 'WorkerOffersCreated'
          AND payload ->> 'bookingId' = ?
        ORDER BY id
        """.trimIndent(),
        { result, _ ->
            val payload = mapper.readTree(result.getString("payload"))
            WorkerPushTarget(
                UUID.fromString(result.getString("recipient_id")),
                payload.path("offers").toList(),
            )
        },
        bookingId.toString(),
    ).groupBy(WorkerPushTarget::workerId).map { (workerId, targets) ->
        WorkerPushTarget(
            workerId,
            targets.flatMap(WorkerPushTarget::jobs).distinctBy { it.path("jobId").asText() },
        )
    }
}
