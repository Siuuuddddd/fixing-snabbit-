package com.snabbit.prototype.mobile.domain.auth

enum class AuthFailureReason {
    CANCELLED,
    NOT_CONFIGURED,
    NO_GOOGLE_ACCOUNT,
    PLAY_SERVICES_UNAVAILABLE,
    GOOGLE_CONFIGURATION,
    INVALID_GOOGLE_TOKEN,
    EMAIL_NOT_VERIFIED,
    WORKER_NOT_REGISTERED,
    INVALID_SELECTION_TOKEN,
    ACCOUNT_DISABLED,
    ACCOUNT_LINK_CONFLICT,
    SESSION_REVOKED,
    REFRESH_IN_PROGRESS,
    SHARED_WORKER_SESSION_REQUIRED,
    PHONE_REQUIRED,
    RATE_LIMITED,
    INVALID_CREDENTIALS,
    WEAK_PASSWORD,
    INVALID_OR_EXPIRED_CODE,
    TOO_MANY_ATTEMPTS,
    TERMS_NOT_ACCEPTED,
    PASSWORD_NOT_SET,
    VALIDATION_ERROR,
    OFFLINE,
    SERVER,
    WORKER_PROFILE_REQUIRED,
    UNKNOWN,
}

class AuthFailure(
    val reason: AuthFailureReason,
    override val message: String? = null,
    val retryAfterSeconds: Long? = null,
    val fieldErrors: Map<String, String> = emptyMap(),
    val passwordReason: String? = null,
) : Exception(message)

interface AuthRepository {
    suspend fun signInWithGoogle(credential: GoogleIdToken, device: AuthDevice): GoogleSignInResult =
        error("Google sign-in is not implemented")
    suspend fun selectWorkerProfile(
        credential: GoogleIdToken,
        selectionToken: String,
        workerId: String,
        device: AuthDevice,
    ): AuthSession = error("Worker selection is not implemented")
    suspend fun switchWorkerProfile(workerId: String): AuthSession = error("Worker switching is not implemented")
    suspend fun refreshSession(): AuthSession = error("Session refresh is not implemented")
    suspend fun signOut(allDevices: Boolean) = Unit
    suspend fun saveCustomerPhone(phoneNumber: String): String = error("Phone update is not implemented")
}

data class GoogleIdToken(val value: String, val nonce: String)

data class AuthDevice(val id: String? = null, val name: String? = null)

data class SessionUser(
    val id: String,
    val name: String,
    val email: String,
    val pictureUrl: String?,
    val isNewUser: Boolean,
    val role: String,
    val phoneNumber: String? = null,
    val emailVerified: Boolean = false,
    val hasPassword: Boolean = false,
    val authProviders: Set<String> = emptySet(),
)

data class AuthSession(
    val accessToken: String,
    val tokenType: String,
    val expiresAtEpochSeconds: Long,
    val refreshToken: String,
    val user: SessionUser,
)

data class WorkerPickerOption(val workerId: String, val name: String, val tier: String)

sealed interface GoogleSignInResult {
    data class Authenticated(val session: AuthSession) : GoogleSignInResult
    data class WorkerSelectionRequired(
        val selectionToken: String,
        val expiresAtEpochSeconds: Long,
        val workers: List<WorkerPickerOption>,
    ) : GoogleSignInResult
}
