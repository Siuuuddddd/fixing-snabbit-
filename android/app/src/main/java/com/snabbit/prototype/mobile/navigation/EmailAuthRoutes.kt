package com.snabbit.prototype.mobile.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data class VerifyEmailRoute(val email: String) : NavKey

@Serializable
data class ForgotPasswordRoute(val email: String = "") : NavKey

@Serializable
data object ChangePasswordRoute : NavKey
