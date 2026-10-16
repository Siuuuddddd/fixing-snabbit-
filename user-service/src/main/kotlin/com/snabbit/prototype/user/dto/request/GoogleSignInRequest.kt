package com.snabbit.prototype.user.dto.request

import jakarta.validation.constraints.NotBlank

data class GoogleSignInRequest(
    @field:NotBlank val idToken: String,
    val nonce: String? = null,
    val deviceId: String? = null,
    val deviceName: String? = null,
    val rememberMe: Boolean = true,
)
