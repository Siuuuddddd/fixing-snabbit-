package com.snabbit.prototype.booking.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.snabbit.prototype.booking.domain.OutboxEvent
import com.snabbit.prototype.booking.repository.OutboxEventRepository
import java.time.Instant
import java.util.UUID
import org.springframework.stereotype.Component

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
