package com.snabbit.prototype.user.dto.response

data class OtpVerificationResult(
    val status: String,
    val accessToken: String? = null,
    val tokenType: String? = null,
    val expiresInSeconds: Long? = null,
    val registrationToken: String? = null,
)
