package com.snabbit.prototype.worker.events

import com.fasterxml.jackson.databind.JsonNode
import java.time.Instant
import java.util.UUID

const val CURRENT_EVENT_SCHEMA_VERSION = 1

data class EventEnvelope(
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

data class ClaimedOutboxEvent(
    val id: UUID,
    val aggregateType: String,
    val aggregateId: String,
    val eventType: String,
    val payload: String,
    val occurredAt: Instant,
    val schemaVersion: Int,
)
