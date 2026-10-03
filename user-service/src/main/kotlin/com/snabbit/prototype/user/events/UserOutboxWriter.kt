package com.snabbit.prototype.user.events

import com.fasterxml.jackson.databind.ObjectMapper
import com.snabbit.prototype.user.domain.UserOutboxEvent
import com.snabbit.prototype.user.repository.UserOutboxRepository
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

@Component
class UserOutboxWriter(
    private val repository: UserOutboxRepository,
    private val objectMapper: ObjectMapper,
) {
    fun append(userId: UUID, eventType: String, payload: Map<String, Any?>, occurredAt: Instant) {
        repository.save(
            UserOutboxEvent(
                id = UUID.randomUUID(),
                aggregateType = "User",
                aggregateId = userId.toString(),
                eventType = eventType,
                payload = objectMapper.writeValueAsString(payload),
                occurredAt = occurredAt,
            ),
        )
    }
}
