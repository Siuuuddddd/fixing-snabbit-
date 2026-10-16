package com.snabbit.prototype.booking.dto.response

import java.util.UUID

data class WorkerOptionsSnapshot(
    val bookingId: UUID,
    val visitIndex: Int? = null,
    val favourites: List<WorkerOptionSnapshot>,
    val others: List<WorkerOptionSnapshot>,
    val veterans: List<WorkerOptionSnapshot> = others.filter { it.tier == "VETERAN" },
)
