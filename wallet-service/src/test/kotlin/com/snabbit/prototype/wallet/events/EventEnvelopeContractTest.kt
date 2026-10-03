package com.snabbit.prototype.wallet.events

import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.Test

class EventEnvelopeContractTest {
    private val mapper = ObjectMapper().findAndRegisterModules()

    @Test
    fun `emits version one and accepts an unversioned rolling-upgrade event`() {
        val node = mapper.valueToTree<ObjectNode>(event())
        assertEquals(1, node.path("schemaVersion").asInt())
        node.remove("schemaVersion")

        val legacy = mapper.treeToValue(node, EventEnvelope::class.java)

        assertEquals(1, legacy.schemaVersion)
    }

    @Test
    fun `rejects an unsupported future event version`() {
        val node = mapper.valueToTree<ObjectNode>(event()).put("schemaVersion", 2)

        assertThrows<Exception> { mapper.treeToValue(node, EventEnvelope::class.java) }
    }

    private fun event() = EventEnvelope(
        UUID.randomUUID(), "WalletPaymentSucceeded", "Wallet", UUID.randomUUID().toString(),
        Instant.parse("2026-10-02T04:00:00Z"), mapper.createObjectNode(),
    )
}
