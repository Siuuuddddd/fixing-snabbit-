package com.snabbit.prototype.support.application

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.UUID

class SupportServiceTest {
    private val bookings = mockk<SupportBookingClient>()
    private val transactions = mockk<SupportTransaction>()
    private val service = SupportService(bookings, transactions, SupportProperties(Duration.ofMillis(1)))

    @Test
    fun `customer can open support only for owned booking`() {
        val customerId = UUID.randomUUID()
        val bookingId = UUID.randomUUID()
        every { bookings.ownership(bookingId) } returns BookingOwnershipContext(bookingId, UUID.randomUUID())

        assertThrows(SupportRequestRejected::class.java) {
            service.open(customerId, bookingId, "Help", "Worker has not arrived")
        }

        verify(exactly = 0) { transactions.open(any(), any(), any(), any()) }
    }

    @Test
    fun `stream resumes after caller cursor and emits durable sequence`() = runBlocking {
        val ticketId = UUID.randomUUID()
        val customerId = UUID.randomUUID()
        val expected = SupportMessageSnapshot(43, ticketId, "AGENT", UUID.randomUUID(), "On it", Instant.now())
        every { transactions.requireCustomer(ticketId, customerId) } returns Unit
        every { transactions.messages(ticketId, customerId, 42) } returns listOf(expected)

        val result = service.stream(ticketId, customerId, 42).first()

        assertEquals(expected, result)
    }

    @Test
    fun `agent stream reads ticket without customer ownership filter`() = runBlocking {
        val ticketId = UUID.randomUUID()
        val expected = SupportMessageSnapshot(8, ticketId, "CUSTOMER", UUID.randomUUID(), "Help", Instant.now())
        every { transactions.agentMessages(ticketId, 7) } returns listOf(expected)

        val result = service.agentStream(ticketId, 7).first()

        assertEquals(expected, result)
        verify(exactly = 0) { transactions.requireCustomer(any(), any()) }
    }
}
