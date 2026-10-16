package com.snabbit.prototype.booking.dto.request

import java.util.UUID

data class WorkerSelectionRequest(
    val visitIndex: Int,
    val workerId: UUID,
)
