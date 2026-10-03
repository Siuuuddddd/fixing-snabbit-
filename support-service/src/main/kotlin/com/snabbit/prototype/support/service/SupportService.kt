package com.snabbit.prototype.support.service

import com.snabbit.prototype.support.domain.enums.SupportSenderType
import com.snabbit.prototype.support.domain.enums.SupportTicketStatus
import com.snabbit.prototype.support.dto.response.SupportMessageSnapshot
import com.snabbit.prototype.support.dto.response.SupportTicketSnapshot
import com.snabbit.prototype.support.exception.SupportRequestRejected
import java.util.UUID
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Service

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

    fun agentTickets(status: SupportTicketStatus): List<SupportTicketSnapshot> = transactions.agentTickets(status)

    fun agentMessages(ticketId: UUID, afterMessageId: Long): List<SupportMessageSnapshot> =
        transactions.agentMessages(ticketId, afterMessageId)

    fun agentStream(ticketId: UUID, afterMessageId: Long): Flow<SupportMessageSnapshot> = flow {
        var cursor = afterMessageId.coerceAtLeast(0)
        while (currentCoroutineContext().isActive) {
            val batch = withContext(Dispatchers.IO) { transactions.agentMessages(ticketId, cursor) }
            batch.forEach { message ->
                emit(message)
                cursor = message.messageId
            }
            delay(properties.streamPollDelay.toMillis())
        }
    }

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
