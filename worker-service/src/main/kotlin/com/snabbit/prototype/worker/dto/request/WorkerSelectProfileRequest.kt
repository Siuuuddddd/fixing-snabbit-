package com.snabbit.prototype.worker.dto.request

import jakarta.validation.constraints.NotBlank
import java.util.UUID

data class WorkerSelectProfileRequest(
    @field:NotBlank val idToken: String,
    val nonce: String? = null,
    @field:NotBlank val selectionToken: String,
    val workerId: UUID,
    val deviceId: String? = null,
    val deviceName: String? = null,
)
