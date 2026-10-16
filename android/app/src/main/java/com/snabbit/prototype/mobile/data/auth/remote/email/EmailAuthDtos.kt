package com.snabbit.prototype.mobile.data.auth.remote.email

import kotlinx.serialization.Serializable

@Serializable
data class EmailRegisterRequestDto(
    val name: String,
    val email: String,
    val password: String,
    val termsAccepted: Boolean,
    val deviceId: String? = null,
    val deviceName: String? = null,
)

@Serializable
data class VerifyEmailRequestDto(
    val email: String,
    val code: String,
    val deviceId: String? = null,
    val deviceName: String? = null,
    val rememberMe: Boolean = true,
)

@Serializable
data class EmailLoginRequestDto(
    val email: String,
    val password: String,
    val deviceId: String? = null,
    val deviceName: String? = null,
    val rememberMe: Boolean = true,
)

@Serializable data class EmailOnlyRequestDto(val email: String)
@Serializable data class ResetPasswordRequestDto(val email: String, val code: String, val newPassword: String)
@Serializable data class ChangePasswordRequestDto(val currentPassword: String, val newPassword: String)
@Serializable data class AcceptedAuthRequestDto(val status: String)

