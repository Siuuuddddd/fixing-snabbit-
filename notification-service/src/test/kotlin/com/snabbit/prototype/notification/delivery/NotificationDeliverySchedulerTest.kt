package com.snabbit.prototype.notification.delivery

import com.snabbit.prototype.notification.service.NotificationProperties
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class NotificationDeliverySchedulerTest {
    private val store = mockk<NotificationDeliveryStore>(relaxed = true)
    private val query = mockk<NotificationDeliveryQuery>()
    private val contacts = mockk<NotificationContactClient>()
    private val push = mockk<PushDeliveryAdapter>(relaxed = true)
    private val sms = mockk<SmsDeliveryAdapter>(relaxed = true)
    private val now = Instant.parse("2026-10-02T04:00:00Z")
    private val properties = NotificationProperties(
        "notification-service", "booking-events", "wallet-events", Duration.ofSeconds(1),
        Duration.ofSeconds(5), Duration.ofSeconds(30), 50, 5,
    )
    private val scheduler = NotificationDeliveryScheduler(
        store, query, contacts, push, sms, properties, Clock.fixed(now, ZoneOffset.UTC),
    )

    @Test
    fun `leased push delivery uses active devices and is acknowledged`() {
        val delivery = ClaimedDelivery(UUID.randomUUID(), 1, UUID.randomUUID(), "PUSH", "Confirmed", "Ready", 1)
        every { store.claimBatch(any(), now, now.plusSeconds(30), 50) } returns listOf(delivery)
        every { query.pushTokens(delivery.customerId) } returns listOf("a-valid-device-token-12345")

        scheduler.deliverPending()

        verify(exactly = 1) { push.send(listOf("a-valid-device-token-12345"), "Confirmed", "Ready") }
        verify(exactly = 1) { store.markDelivered(delivery.deliveryId, any(), now) }
    }

    @Test
    fun `delivery failure is released for bounded retry`() {
        val delivery = ClaimedDelivery(UUID.randomUUID(), 1, UUID.randomUUID(), "PUSH", "Confirmed", "Ready", 2)
        every { store.claimBatch(any(), now, now.plusSeconds(30), 50) } returns listOf(delivery)
        every { query.pushTokens(delivery.customerId) } returns emptyList()
        every { push.send(emptyList(), any(), any()) } throws IllegalStateException("no device")

        scheduler.deliverPending()

        verify(exactly = 1) { store.releaseFailure(delivery.deliveryId, any(), 2, 5, now, "no device") }
    }
}
