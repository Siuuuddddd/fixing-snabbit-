package com.snabbit.prototype.user.dto.response

data class PhoneVerificationAccepted(
    val maskedPhone: String,
    val retryAfterSeconds: Long,
)
