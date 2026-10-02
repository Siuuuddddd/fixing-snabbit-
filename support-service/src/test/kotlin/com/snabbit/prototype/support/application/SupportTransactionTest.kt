package com.snabbit.prototype.support.application

import com.snabbit.prototype.support.domain.SupportMessage
import com.snabbit.prototype.support.domain.SupportSenderType
import com.snabbit.prototype.support.domain.SupportTicket
import com.snabbit.prototype.support.domain.SupportTicketStatus
import com.snabbit.prototype.support.persistence.SupportMessageRepository
import com.snabbit.prototype.support.persistence.SupportTicketRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class SupportTransactionTest {
    private val tickets = mockk<SupportTicketRepository>()
    private val messages = mockk<SupportMessageRepository>()
    private val now = Instant.parse("2026-10-02T04:00:00Z")
    private val service = SupportTransaction(tickets, messages, Clock.fixed(now, ZoneOffset.UTC))

    @Test
    fun `opening ticket stores initial customer message and replays idempotently by booking`() {
        val bookingId = UUID.randomUUID()
        val customerId = UUID.randomUUID()
        var savedTicket: SupportTicket? = null
        every { tickets.findByBookingId(bookingId) } answers { savedTicket }
        every { tickets.save(any()) } answers { firstArg<SupportTicket>().also { savedTicket = it } }
        every { messages.save(any()) } answers { firstArg() }

        val first = service.open(customerId, bookingId, " Arrival issue ", " Please help ")
        val replay = service.open(customerId, bookingId, "Ignored", "Ignored")

        assertEquals(first, replay)
        assertEquals("Arrival issue", first.subject)
        verify(exactly = 1) { tickets.save(any()) }
        verify(exactly = 1) {
            messages.save(match<SupportMessage> { it.senderType == SupportSenderType.CUSTOMER && it.body == "Please help" })
        }
    }

    @Test
    fun `resolved ticket rejects new messages`() {
        val ticket = SupportTicket(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), SupportTicketStatus.RESOLVED, "Done", now, now,
        )
        every { tickets.findForUpdate(ticket.id) } returns ticket

        assertThrows(SupportRequestRejected::class.java) {
            service.send(ticket.id, SupportSenderType.CUSTOMER, ticket.customerId, "Again", ticket.customerId)
        }

        verify(exactly = 0) { messages.save(any()) }
    }
}
