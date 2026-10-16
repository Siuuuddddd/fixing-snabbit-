package com.snabbit.prototype.booking.dto.response

data class ArrivalOtpStatusSnapshot(
    val otpStatus: String,
    val displayText: String,
    val remainingSends: Int? = null,
    val retryAfterSeconds: Long? = null,
)
