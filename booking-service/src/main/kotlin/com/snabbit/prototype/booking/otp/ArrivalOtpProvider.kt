package com.snabbit.prototype.booking.otp

data class ArrivalOtpStartResult(val providerReference: String)

enum class ArrivalOtpCheckResult { APPROVED, WRONG_CODE, EXPIRED, LOCKED }

interface ArrivalOtpProvider {
    fun start(phoneE164: String): ArrivalOtpStartResult
    fun check(phoneE164: String, code: String): ArrivalOtpCheckResult
}

class ArrivalOtpProviderUnavailable : RuntimeException("Arrival verification provider is temporarily unavailable")
