package com.snabbit.prototype.worker.dto.response

data class WorkerOtpRequestResult(
    val status: String = "OTP_SENT",
    val expiresInSeconds: Long,
    val debugOtp: String?,
)
