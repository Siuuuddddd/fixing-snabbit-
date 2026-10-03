package com.snabbit.prototype.support.dto.response

import java.time.Instant
import java.util.UUID

data class SupportMessageSnapshot(
    val messageId: Long,
    val ticketId: UUID,
    val senderType: String,
    val senderId: UUID?,
    val body: String,
    val createdAt: Instant,
)
