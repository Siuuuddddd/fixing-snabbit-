package com.snabbit.prototype.user.dto.response

data class PhoneOtpAccepted(
    val status: String = "REQUEST_ACCEPTED",
    val retryAfterSeconds: Long,
)
