package com.snabbit.prototype.worker.events

import com.fasterxml.jackson.databind.ObjectMapper
import com.snabbit.prototype.worker.domain.WorkerOutboxEvent
import com.snabbit.prototype.worker.repository.WorkerOutboxRepository
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

@Component
class WorkerOutboxWriter(
    private val repository: WorkerOutboxRepository,
    private val objectMapper: ObjectMapper,
) {
    fun append(workerId: UUID, eventType: String, payload: Map<String, Any?>, occurredAt: Instant) {
        repository.save(
            WorkerOutboxEvent(
                UUID.randomUUID(), "Worker", workerId.toString(), eventType,
                payload = objectMapper.writeValueAsString(payload), occurredAt = occurredAt,
            ),
        )
    }
}
