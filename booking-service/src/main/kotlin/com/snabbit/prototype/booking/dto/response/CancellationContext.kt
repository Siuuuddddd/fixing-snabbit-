package com.snabbit.prototype.booking.dto.response

import java.time.Instant
import java.util.UUID

data class CancellationContext(
    val bookingId: UUID,
    val customerId: UUID,
    val status: String,
    val quotedTotalMinor: Long,
    val earliestVisitStartsAt: Instant,
    val allVisitsUnstarted: Boolean,
    val cancellationType: String?,
)
