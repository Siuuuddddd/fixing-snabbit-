package com.snabbit.prototype.mobile.domain.auth

data class OtpChallenge(
    val expiresInSeconds: Long,
)

enum class AuthFailureReason {
    INVALID_OR_EXPIRED_OTP,
    RATE_LIMITED,
    OFFLINE,
    SERVER,
    WORKER_PROFILE_REQUIRED,
    UNKNOWN,
}

class AuthFailure(
    val reason: AuthFailureReason,
    override val message: String? = null,
) : Exception(message)

interface AuthRepository {
    suspend fun requestOtp(phoneNumber: String): OtpChallenge
    suspend fun verifyOtp(phoneNumber: String, otp: String): AuthResult
    suspend fun register(phoneNumber: String, displayName: String, registrationToken: String)
}

sealed interface AuthResult {
    data object Authenticated : AuthResult
    data class RegistrationRequired(val token: String) : AuthResult
}
