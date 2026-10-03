package com.snabbit.prototype.support.service

import com.snabbit.prototype.support.dto.response.SupportTicketSnapshot

import com.snabbit.prototype.support.dto.response.SupportMessageSnapshot

import com.snabbit.prototype.support.domain.enums.SupportTicketStatus

import com.snabbit.prototype.support.domain.enums.SupportSenderType

import com.snabbit.prototype.support.domain.SupportMessage
import com.snabbit.prototype.support.domain.SupportTicket
import com.snabbit.prototype.support.repository.SupportMessageRepository
import com.snabbit.prototype.support.repository.SupportTicketRepository
import com.snabbit.prototype.support.security.InternalServiceClientFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Service
class SupportTransaction(
    private val tickets: SupportTicketRepository,
    private val messages: SupportMessageRepository,
    private val clock: Clock,
) {
    @Transactional
    fun open(
        customerId: UUID,
        bookingId: UUID,
        rawSubject: String,
        initialMessage: String,
    ): SupportTicketSnapshot {
        val subject = normalized(rawSubject, 160, "Subject")
        val body = normalized(initialMessage, 2_000, "Message")
        tickets.findByBookingId(bookingId)?.let { existing ->
            requireCustomer(existing, customerId)
            return ticketSnapshot(existing)
        }
        val now = clock.instant()
        val ticket = tickets.save(
            SupportTicket(UUID.randomUUID(), bookingId, customerId, SupportTicketStatus.OPEN, subject, now, now),
        )
        messages.save(SupportMessage(ticketId = ticket.id, senderType = SupportSenderType.CUSTOMER, senderId = customerId, body = body, createdAt = now))
        return ticketSnapshot(ticket)
    }

    @Transactional
    fun send(
        ticketId: UUID,
        senderType: SupportSenderType,
        senderId: UUID,
        rawBody: String,
        expectedCustomerId: UUID?,
    ): SupportMessageSnapshot {
        val ticket = tickets.findForUpdate(ticketId) ?: throw SupportResourceNotFound("Support ticket was not found")
        if (expectedCustomerId != null) requireCustomer(ticket, expectedCustomerId)
        val now = clock.instant()
        try {
            ticket.touch(now)
        } catch (error: IllegalStateException) {
            throw SupportRequestRejected(error.message.orEmpty())
        }
        return messageSnapshot(
            messages.save(
                SupportMessage(
                    ticketId = ticket.id,
                    senderType = senderType,
                    senderId = senderId,
                    body = normalized(rawBody, 2_000, "Message"),
                    createdAt = now,
                ),
            ),
        )
    }

    @Transactional
    fun resolve(ticketId: UUID): SupportTicketSnapshot {
        val ticket = tickets.findForUpdate(ticketId) ?: throw SupportResourceNotFound("Support ticket was not found")
        ticket.resolve(clock.instant())
        return ticketSnapshot(ticket)
    }

    @Transactional(readOnly = true)
    fun requireCustomer(ticketId: UUID, customerId: UUID) {
        val ticket = tickets.findById(ticketId).orElseThrow { SupportResourceNotFound("Support ticket was not found") }
        requireCustomer(ticket, customerId)
    }

    @Transactional(readOnly = true)
    fun messages(ticketId: UUID, customerId: UUID, afterId: Long): List<SupportMessageSnapshot> {
        requireCustomer(ticketId, customerId)
        return messages.findTop100ByTicketIdAndIdGreaterThanOrderByIdAsc(ticketId, afterId).map(::messageSnapshot)
    }

    @Transactional(readOnly = true)
    fun agentTickets(status: SupportTicketStatus): List<SupportTicketSnapshot> =
        tickets.findTop100ByStatusOrderByUpdatedAtDesc(status).map(::ticketSnapshot)

    @Transactional(readOnly = true)
    fun agentMessages(ticketId: UUID, afterId: Long): List<SupportMessageSnapshot> {
        if (!tickets.existsById(ticketId)) throw SupportResourceNotFound("Support ticket was not found")
        return messages.findTop100ByTicketIdAndIdGreaterThanOrderByIdAsc(ticketId, afterId.coerceAtLeast(0))
            .map(::messageSnapshot)
    }

    private fun requireCustomer(ticket: SupportTicket, customerId: UUID) {
        if (ticket.customerId != customerId) throw SupportRequestRejected("Customer does not own this support ticket")
    }

    private fun normalized(value: String, maxLength: Int, label: String): String = value.trim().also {
        if (it.isBlank() || it.length > maxLength) throw SupportRequestRejected("$label must contain 1 to $maxLength characters")
    }

    private fun ticketSnapshot(ticket: SupportTicket) = SupportTicketSnapshot(
        ticket.id,
        ticket.bookingId,
        ticket.customerId,
        ticket.status.name,
        ticket.subject,
        ticket.createdAt,
        ticket.updatedAt,
    )

    private fun messageSnapshot(message: SupportMessage) = SupportMessageSnapshot(
        message.id,
        message.ticketId,
        message.senderType.name,
        message.senderId,
        message.body,
        message.createdAt,
    )
}
