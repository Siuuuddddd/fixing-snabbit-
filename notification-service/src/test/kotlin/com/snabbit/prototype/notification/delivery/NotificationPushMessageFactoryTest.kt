package com.snabbit.prototype.notification.delivery

import com.fasterxml.jackson.databind.ObjectMapper
import com.snabbit.prototype.notification.domain.enums.RecipientType
import com.snabbit.prototype.notification.dto.response.ClaimedDelivery
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NotificationPushMessageFactoryTest {
    private val factory = NotificationPushMessageFactory(ObjectMapper())

    @Test
    fun `customer booking notification contains deep link data`() {
        val result = factory.create(
            delivery(
                RecipientType.CUSTOMER,
                "BookingConfirmed",
                """{"bookingId":"booking-1"}""",
            ),
        )

        assertEquals("bookings", result.channelId)
        assertEquals(
            mapOf("type" to "BookingConfirmed", "bookingId" to "booking-1", "channelId" to "bookings"),
            result.data,
        )
    }

    @Test
    fun `worker notification contains job route data`() {
        val result = factory.create(
            delivery(
                RecipientType.WORKER,
                "WorkerOffersCreated",
                """{
                    "bookingId":"booking-1",
                    "offers":[{"jobId":"job-1","visitIndex":2}]
                }""",
            ),
        )

        assertEquals("jobs", result.channelId)
        assertEquals(
            mapOf(
                "type" to "WorkerOffersCreated",
                "bookingId" to "booking-1",
                "jobId" to "job-1",
                "visitIndex" to "2",
                "channelId" to "jobs",
            ),
            result.data,
        )
    }

    @Test
    fun `wallet notification uses the payments channel`() {
        val result = factory.create(
            delivery(
                RecipientType.CUSTOMER,
                "WalletTopUpSucceeded",
                """{"walletId":"wallet-1"}""",
            ),
        )

        assertEquals("payments", result.channelId)
        assertEquals("wallet-1", result.data["walletId"])
        assertEquals("payments", result.data["channelId"])
    }

    private fun delivery(recipient: RecipientType, type: String, payload: String) = ClaimedDelivery(
        UUID.randomUUID(), 1, recipient, UUID.randomUUID(), "PUSH", type, "Title", "Body", payload, 1,
    )
}
