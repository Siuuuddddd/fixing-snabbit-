package com.snabbit.prototype.wallet.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.snabbit.prototype.wallet.domain.WalletOutboxEvent
import com.snabbit.prototype.wallet.repository.WalletOutboxRepository
import java.time.Instant
import java.util.UUID
import org.springframework.stereotype.Component

@Component
class WalletOutboxWriter(
    private val repository: WalletOutboxRepository,
    private val objectMapper: ObjectMapper,
) {
    fun append(aggregateId: UUID, eventType: String, payload: Map<String, Any>, occurredAt: Instant) {
        repository.save(
            WalletOutboxEvent(
                id = UUID.randomUUID(),
                aggregateType = "Wallet",
                aggregateId = aggregateId.toString(),
                eventType = eventType,
                payload = objectMapper.writeValueAsString(payload),
                occurredAt = occurredAt,
            ),
        )
    }
}
