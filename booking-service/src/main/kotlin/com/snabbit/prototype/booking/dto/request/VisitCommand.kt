package com.snabbit.prototype.booking.dto.request

import java.time.Instant
import java.time.Duration
import java.util.UUID
import com.snabbit.prototype.booking.domain.enums.AssignmentType

data class VisitCommand(
    val workerId: UUID,
    val startsAt: Instant,
    val duration: Duration? = null,
    val quotedPriceMinor: Long = 0,
    val assignmentType: AssignmentType = AssignmentType.PREFERRED,
    val preferredWorkerId: UUID? = null,
    val assignedWorkerTier: String = "STANDARD",
)
