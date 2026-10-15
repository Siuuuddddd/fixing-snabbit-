package com.snabbit.prototype.mobile.navigation

import androidx.compose.runtime.staticCompositionLocalOf

data class ProfileNavigation(
    val openChangePassword: () -> Unit,
    val back: () -> Unit,
)

val LocalProfileNavigation = staticCompositionLocalOf<ProfileNavigation> {
    error("ProfileNavigation is not available")
}
