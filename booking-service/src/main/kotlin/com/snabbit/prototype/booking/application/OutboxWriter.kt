package com.snabbit.prototype.booking.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.snabbit.prototype.booking.domain.OutboxEvent
import com.snabbit.prototype.booking.persistence.OutboxEventRepository
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

@Component
class OutboxWriter(
    private val repository: OutboxEventRepository,
    private val objectMapper: ObjectMapper,
) {
    fun append(bookingId: UUID, eventType: String, payload: Map<String, Any>, occurredAt: Instant) {
        repository.save(
            OutboxEvent(
                id = UUID.randomUUID(),
                aggregateType = "Booking",
                aggregateId = bookingId.toString(),
                eventType = eventType,
                payload = objectMapper.writeValueAsString(payload),
                occurredAt = occurredAt,
            ),
        )
    }
}
