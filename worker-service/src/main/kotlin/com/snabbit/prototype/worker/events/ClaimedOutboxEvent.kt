package com.snabbit.prototype.worker.events

import com.fasterxml.jackson.databind.JsonNode
import java.time.Instant
import java.util.UUID

data class ClaimedOutboxEvent(
    val id: UUID,
    val aggregateType: String,
    val aggregateId: String,
    val eventType: String,
    val payload: String,
    val occurredAt: Instant,
    val schemaVersion: Int,
)
