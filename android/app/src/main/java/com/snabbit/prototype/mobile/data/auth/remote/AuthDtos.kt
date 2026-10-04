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
    val accessToken: String,
    val tokenType: String,
    val expiresInSeconds: Long,
)
