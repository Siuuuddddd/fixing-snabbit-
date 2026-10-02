package com.snabbit.prototype.user.plus

import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class PlusWalletEventHandlerTest {
    private val processed = mockk<PlusProcessedMessageStore>()
    private val plus = mockk<PlusService>(relaxed = true)
    private val handler = PlusWalletEventHandler(processed, plus, "user-service-plus-purchases")
    private val mapper = ObjectMapper()

    @Test
    fun `verified wallet purchase activates entitlement exactly once`() {
        val occurredAt = Instant.parse("2026-10-02T04:00:00Z")
        val purchaseId = UUID.randomUUID()
        val customerId = UUID.randomUUID()
        val payload = mapper.createObjectNode()
            .put("purchaseId", purchaseId.toString())
            .put("customerId", customerId.toString())
            .put("durationDays", 30)
            .put("passCount", 4)
            .put("passDiscountMinor", 3_000)
            .put("serviceCode", "")
        val event = BookingEventEnvelope(
            UUID.randomUUID(), "PlusSubscriptionPurchased", "Wallet", UUID.randomUUID().toString(),
            occurredAt, payload,
        )
        every { processed.claim("user-service-plus-purchases", event.eventId, occurredAt) } returns true andThen false

        handler.handle(event)
        handler.handle(event)

        verify(exactly = 1) {
            plus.activatePurchase(customerId, purchaseId, occurredAt, 30, 4, 3_000, null)
        }
    }
}
