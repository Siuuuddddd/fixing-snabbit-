package com.snabbit.prototype.booking.dto.response

import java.time.Instant
import java.util.UUID

data class VisitSnapshot(
    val visitIndex: Int,
    val workerId: UUID,
    val startsAt: Instant,
    val endsAt: Instant,
    val holdExpiresAt: Instant,
    val offerId: UUID,
    val offerStatus: String,
    val offerExpiresAt: Instant,
    val jobStatus: String?,
    val arrivedAt: Instant?,
    val startedAt: Instant?,
    val completedAt: Instant?,
)
