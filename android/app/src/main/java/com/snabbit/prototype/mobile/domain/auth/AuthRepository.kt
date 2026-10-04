package com.snabbit.prototype.mobile.domain.auth

data class OtpChallenge(
    val expiresInSeconds: Long,
)

enum class AuthFailureReason {
    INVALID_OR_EXPIRED_OTP,
    RATE_LIMITED,
    OFFLINE,
    SERVER,
    UNKNOWN,
}

class AuthFailure(
    val reason: AuthFailureReason,
    override val message: String? = null,
) : Exception(message)

interface AuthRepository {
    suspend fun requestOtp(phoneNumber: String): OtpChallenge
    suspend fun verifyOtp(phoneNumber: String, otp: String)
}
