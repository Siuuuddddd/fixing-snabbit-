package com.snabbit.prototype.booking.dto.response

import java.util.UUID

data class WorkerOptionsSnapshot(
    val bookingId: UUID,
    val favourites: List<WorkerOptionSnapshot>,
    val veterans: List<WorkerOptionSnapshot>,
)
