package com.snabbit.prototype.booking.dto.response

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
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
    val date: LocalDate = LocalDate.ofEpochDay(0),
    val startTime: LocalTime = LocalTime.MIDNIGHT,
    val durationMinutes: Int = 0,
    val quotedPriceMinor: Long = 0,
    val assignmentType: String = "PREFERRED",
    val refundStatus: String = "NONE",
    val refundAmountMinor: Long? = null,
    val refundIdempotencyKey: UUID? = null,
    val cancelledAt: Instant? = null,
)
