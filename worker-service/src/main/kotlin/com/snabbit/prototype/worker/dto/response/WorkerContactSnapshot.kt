package com.snabbit.prototype.worker.dto.response

import java.util.UUID

data class WorkerContactSnapshot(
    val workerId: UUID,
    val phoneNumber: String,
)
