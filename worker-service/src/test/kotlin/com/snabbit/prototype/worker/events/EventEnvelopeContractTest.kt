package com.snabbit.prototype.worker.events

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.UUID

class EventEnvelopeContractTest {
    private val mapper = ObjectMapper().findAndRegisterModules()

    @Test
    fun `worker event contract is version one and rejects future versions`() {
        val event = EventEnvelope(
            UUID.randomUUID(), "WorkerAvailabilityUpdated", "Worker", UUID.randomUUID().toString(),
            Instant.parse("2026-10-03T04:00:00Z"), mapper.createObjectNode(),
        )
        assertEquals(1, mapper.valueToTree<ObjectNode>(event).path("schemaVersion").asInt())
        assertThrows<IllegalArgumentException> { event.copy(schemaVersion = 2) }
    }
}
