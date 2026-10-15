package com.snabbit.prototype.mobile.data.auth.remote

import kotlinx.serialization.Serializable

@Serializable
data class OtpRequestDto(
    val phoneNumber: String,
)

@Serializable
data class OtpVerificationDto(
    val phoneNumber: String,
    val otp: String,
)

@Serializable
data class OtpChallengeDto(
    val status: String,
    val expiresInSeconds: Long,
    val debugOtp: String? = null,
)

@Serializable
data class AccessTokenDto(
    val accessToken: String? = null,
    val tokenType: String? = null,
    val expiresInSeconds: Long? = null,
    val status: String? = null,
    val registrationToken: String? = null,
)

@Serializable
data class CustomerRegistrationDto(
    val phoneNumber: String,
    val displayName: String,
    val registrationToken: String,
)

@Serializable
data class GoogleSignInRequestDto(
    val idToken: String,
    val nonce: String? = null,
    val deviceId: String? = null,
    val deviceName: String? = null,
)

@Serializable
data class WorkerSelectProfileRequestDto(
    val idToken: String,
    val nonce: String? = null,
    val selectionToken: String,
    val workerId: String,
    val deviceId: String? = null,
    val deviceName: String? = null,
)

@Serializable data class WorkerSwitchProfileRequestDto(val workerId: String)
@Serializable data class RefreshSessionRequestDto(val refreshToken: String)

@Serializable
data class SessionUserDto(
    val id: String,
    val name: String,
    val email: String,
    val pictureUrl: String? = null,
    val isNewUser: Boolean = false,
    val role: String,
)

@Serializable
data class SessionResponseDto(
    val accessToken: String,
    val tokenType: String = "Bearer",
    val expiresIn: Long,
    val refreshToken: String,
    val user: SessionUserDto,
    val requiresWorkerSelection: Boolean = false,
)

@Serializable
data class WorkerPickerOptionDto(val workerId: String, val name: String, val tier: String)

@Serializable
data class WorkerPickerResponseDto(
    val code: String,
    val selectionToken: String,
    val expiresIn: Long,
    val workers: List<WorkerPickerOptionDto>,
    val requiresWorkerSelection: Boolean,
)
