package com.snabbit.prototype.notification.service

import com.snabbit.prototype.notification.domain.enums.RecipientType
import com.snabbit.prototype.notification.dto.response.NotificationSnapshot
import io.mockk.every
import io.mockk.mockk
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

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
        every { transactions.history(RecipientType.CUSTOMER, customerId, 10) } returns listOf(expected)

        assertEquals(expected, service.stream(RecipientType.CUSTOMER, customerId, 10).first())
    }
}
