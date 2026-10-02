package com.snabbit.prototype.notification.service

import com.snabbit.prototype.notification.domain.DeliveryChannel
import com.snabbit.prototype.notification.domain.Notification
import com.snabbit.prototype.notification.domain.NotificationDelivery
import com.snabbit.prototype.notification.domain.RecipientType
import com.snabbit.prototype.notification.repository.DeviceRegistrationRepository
import com.snabbit.prototype.notification.repository.NotificationDeliveryRepository
import com.snabbit.prototype.notification.repository.NotificationRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class NotificationTransactionTest {
    private val notifications = mockk<NotificationRepository>()
    private val deliveries = mockk<NotificationDeliveryRepository>()
    private val devices = mockk<DeviceRegistrationRepository>()
    private val now = Instant.parse("2026-10-02T04:00:00Z")
    private val service = NotificationTransaction(notifications, deliveries, devices, Clock.fixed(now, ZoneOffset.UTC))

    @Test
    fun `critical event persists one notification with push and SMS deliveries`() {
        val eventId = UUID.randomUUID()
        val customerId = UUID.randomUUID()
        val savedDeliveries = slot<Iterable<NotificationDelivery>>()
        every { notifications.findByEventId(eventId) } returns null
        every { notifications.save(any()) } answers {
            firstArg<Notification>().let {
                Notification(
                    42, it.eventId, it.recipientType, it.recipientId, it.eventType, it.title, it.body,
                    it.critical, it.payload, it.createdAt,
                )
            }
        }
        every { deliveries.saveAll(capture(savedDeliveries)) } answers { firstArg() }

        val result = service.record(
            eventId, RecipientType.CUSTOMER, customerId, "BookingConfirmed",
            NotificationTemplate("Confirmed", "Worker assigned", true), "{}",
        )

        assertEquals(42, result.notificationId)
        assertEquals(setOf(DeliveryChannel.PUSH, DeliveryChannel.SMS), savedDeliveries.captured.map { it.channel }.toSet())
    }

    @Test
    fun `customer cannot mark another customers notification read`() {
        val notification = Notification(
            7, UUID.randomUUID(), RecipientType.CUSTOMER, UUID.randomUUID(),
            "BookingHeld", "Held", "Slot held", false, "{}", now,
        )
        every { notifications.findForUpdate(7) } returns notification

        assertThrows(NotificationAccessDenied::class.java) {
            service.markRead(RecipientType.CUSTOMER, UUID.randomUUID(), 7)
        }

        assertEquals(null, notification.readAt)
        verify(exactly = 0) { notifications.save(any()) }
    }
}
