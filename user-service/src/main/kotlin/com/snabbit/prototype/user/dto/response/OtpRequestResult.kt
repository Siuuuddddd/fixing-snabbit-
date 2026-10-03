package com.snabbit.prototype.user.dto.response

data class OtpRequestResult(
    val status: String = "OTP_SENT",
    val expiresInSeconds: Long,
    val debugOtp: String?,
)
