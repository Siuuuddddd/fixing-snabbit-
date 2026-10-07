package com.snabbit.prototype.notification.delivery

import com.fasterxml.jackson.databind.ObjectMapper
import com.snabbit.prototype.notification.domain.enums.RecipientType
import com.snabbit.prototype.notification.dto.response.ClaimedDelivery
import com.snabbit.prototype.notification.service.NotificationProperties
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import org.junit.jupiter.api.Test

class NotificationDeliverySchedulerTest {
    private val store = mockk<NotificationDeliveryStore>(relaxed = true)
    private val query = mockk<NotificationDeliveryQuery>(relaxUnitFun = true)
    private val contacts = mockk<NotificationContactClient>()
    private val push = mockk<PushDeliveryAdapter>(relaxed = true)
    private val sms = mockk<SmsDeliveryAdapter>(relaxed = true)
    private val pushMessages = NotificationPushMessageFactory(ObjectMapper())
    private val now = Instant.parse("2026-10-02T04:00:00Z")
    private val properties = NotificationProperties(
        "notification-service", "booking-events", "wallet-events", Duration.ofSeconds(1),
        Duration.ofSeconds(5), Duration.ofSeconds(30), 50, 5,
    )
    private val scheduler = NotificationDeliveryScheduler(
        store, query, contacts, push, sms, pushMessages, properties, Clock.fixed(now, ZoneOffset.UTC),
    )

    @Test
    fun `leased push delivery uses active devices and is acknowledged`() {
        val delivery = ClaimedDelivery(
            UUID.randomUUID(), 1, RecipientType.CUSTOMER, UUID.randomUUID(), "PUSH",
            "BookingConfirmed", "Confirmed", "Ready", "{\"bookingId\":\"booking-1\"}", 1,
        )
        every { store.claimBatch(any(), now, now.plusSeconds(30), 50) } returns listOf(delivery)
        every { query.pushTokens(delivery.recipientType, delivery.recipientId) } returns
            listOf("a-valid-device-token-12345")
        every { push.send(any(), any()) } returns PushDeliveryResult(setOf("a-valid-device-token-12345"))

        scheduler.deliverPending()

        verify(exactly = 1) {
            push.send(
                listOf("a-valid-device-token-12345"),
                PushMessage(
                    "Confirmed", "Ready",
                    mapOf(
                        "type" to "BookingConfirmed",
                        "bookingId" to "booking-1",
                        "channelId" to "bookings",
                    ),
                    "bookings",
                ),
            )
        }
        verify(exactly = 1) { query.deactivatePushTokens(setOf("a-valid-device-token-12345"), now) }
        verify(exactly = 1) { store.markDelivered(delivery.deliveryId, any(), now) }
    }

    @Test
    fun `delivery failure is released for bounded retry`() {
        val delivery = ClaimedDelivery(
            UUID.randomUUID(), 1, RecipientType.WORKER, UUID.randomUUID(), "PUSH",
            "WorkerOfferExpired", "Expired", "Offer expired", "{}", 2,
        )
        every { store.claimBatch(any(), now, now.plusSeconds(30), 50) } returns listOf(delivery)
        every { query.pushTokens(delivery.recipientType, delivery.recipientId) } returns emptyList()
        every { push.send(emptyList(), any()) } throws IllegalStateException("no device")

        scheduler.deliverPending()

        verify(exactly = 1) { store.releaseFailure(delivery.deliveryId, any(), 2, 5, now, "no device") }
    }
}
