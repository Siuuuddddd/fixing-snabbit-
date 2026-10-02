package com.snabbit.prototype.notification.service

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.UUID

class NotificationServiceTest {
    private val transactions = mockk<NotificationTransaction>()
    private val properties = NotificationProperties(
        "notification-service", "booking-events", "wallet-events", Duration.ofMillis(1),
        Duration.ofSeconds(5), Duration.ofSeconds(30), 50, 5,
    )
    private val service = NotificationService(transactions, properties)

    @Test
    fun `live stream resumes strictly after durable notification cursor`() = runBlocking {
        val customerId = UUID.randomUUID()
        val expected = NotificationSnapshot(11, "BookingConfirmed", "Confirmed", "Ready", true, Instant.now(), null)
        every { transactions.history(customerId, 10) } returns listOf(expected)

        assertEquals(expected, service.stream(customerId, 10).first())
    }
}
