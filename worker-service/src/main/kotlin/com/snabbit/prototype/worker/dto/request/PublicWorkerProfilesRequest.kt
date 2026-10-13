package com.snabbit.prototype.worker.dto.request

import java.util.UUID

data class PublicWorkerProfilesRequest(
    val workerIds: List<UUID>,
    val customerId: UUID? = null,
)
