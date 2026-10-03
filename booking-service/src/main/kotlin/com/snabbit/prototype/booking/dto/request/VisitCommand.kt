package com.snabbit.prototype.booking.dto.request

import java.time.Instant
import java.util.UUID

data class VisitCommand(
    val workerId: UUID,
    val startsAt: Instant,
)
