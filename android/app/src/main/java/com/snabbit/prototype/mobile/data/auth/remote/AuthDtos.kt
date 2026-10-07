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
