package com.snabbit.prototype.worker.dto.request

import jakarta.validation.constraints.NotBlank

data class WorkerGoogleSignInRequest(
    @field:NotBlank val idToken: String,
    val nonce: String? = null,
    val deviceId: String? = null,
    val deviceName: String? = null,
)
