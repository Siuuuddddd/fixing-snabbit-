package com.snabbit.prototype.support.application

import com.snabbit.prototype.support.domain.SupportMessage
import com.snabbit.prototype.support.domain.SupportSenderType
import com.snabbit.prototype.support.domain.SupportTicket
import com.snabbit.prototype.support.domain.SupportTicketStatus
import com.snabbit.prototype.support.persistence.SupportMessageRepository
import com.snabbit.prototype.support.persistence.SupportTicketRepository
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

@ConfigurationProperties("integrations")
data class SupportIntegrationProperties(val bookingServiceUrl: String)

@ConfigurationProperties("support")
data class SupportProperties(val streamPollDelay: Duration)

data class BookingOwnershipContext(val bookingId: UUID, val customerId: UUID)

data class SupportTicketSnapshot(
    val ticketId: UUID,
    val bookingId: UUID,
    val customerId: UUID,
    val status: String,
    val subject: String,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class SupportMessageSnapshot(
    val messageId: Long,
    val ticketId: UUID,
    val senderType: String,
    val senderId: UUID?,
    val body: String,
    val createdAt: Instant,
)

class SupportRequestRejected(message: String) : RuntimeException(message)
class SupportResourceNotFound(message: String) : RuntimeException(message)

@Component
class SupportBookingClient(factory: InternalServiceClientFactory, properties: SupportIntegrationProperties) {
    private val client = factory.create(properties.bookingServiceUrl)

    fun ownership(bookingId: UUID): BookingOwnershipContext = client.get()
        .uri("/internal/v1/bookings/{bookingId}/ownership", bookingId)
        .retrieve()
        .body(BookingOwnershipContext::class.java)
        ?: throw SupportRequestRejected("Booking Service returned an empty booking context")
}

@Service
class SupportService(
    private val bookings: SupportBookingClient,
    private val transactions: SupportTransaction,
    private val properties: SupportProperties,
) {
    fun open(customerId: UUID, bookingId: UUID, subject: String, initialMessage: String): SupportTicketSnapshot {
        val ownership = bookings.ownership(bookingId)
        if (ownership.customerId != customerId) throw SupportRequestRejected("Customer does not own this booking")
        return transactions.open(customerId, bookingId, subject, initialMessage)
    }

    fun sendCustomerMessage(ticketId: UUID, customerId: UUID, body: String): SupportMessageSnapshot =
        transactions.send(ticketId, SupportSenderType.CUSTOMER, customerId, body, customerId)

    fun sendAgentMessage(ticketId: UUID, agentId: UUID, body: String): SupportMessageSnapshot =
        transactions.send(ticketId, SupportSenderType.AGENT, agentId, body, null)

    fun resolve(ticketId: UUID): SupportTicketSnapshot = transactions.resolve(ticketId)

    fun stream(ticketId: UUID, customerId: UUID, afterMessageId: Long): Flow<SupportMessageSnapshot> = flow {
        var cursor = afterMessageId.coerceAtLeast(0)
        withContext(Dispatchers.IO) { transactions.requireCustomer(ticketId, customerId) }
        while (currentCoroutineContext().isActive) {
            val messages = withContext(Dispatchers.IO) { transactions.messages(ticketId, customerId, cursor) }
            messages.forEach { message ->
                emit(message)
                cursor = message.messageId
            }
            delay(properties.streamPollDelay.toMillis())
        }
    }
}

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
