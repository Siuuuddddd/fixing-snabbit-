package com.snabbit.prototype.booking.eventing

import com.fasterxml.jackson.databind.JsonNode
import java.time.Instant
import java.util.UUID

data class EventEnvelope(
    val eventId: UUID,
    val eventType: String,
    val aggregateType: String,
    val aggregateId: String,
    val occurredAt: Instant,
    val payload: JsonNode,
)

data class ClaimedOutboxEvent(
    val id: UUID,
    val aggregateType: String,
    val aggregateId: String,
    val eventType: String,
    val payload: String,
    val occurredAt: Instant,
)
