package com.snabbit.prototype.support.dto.response

import java.time.Instant
import java.util.UUID

data class SupportTicketSnapshot(
    val ticketId: UUID,
    val bookingId: UUID,
    val customerId: UUID,
    val status: String,
    val subject: String,
    val createdAt: Instant,
    val updatedAt: Instant,
)
