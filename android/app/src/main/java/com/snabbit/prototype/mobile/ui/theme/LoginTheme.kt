package com.snabbit.prototype.mobile.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class LoginColors(
    val accent: Color,
    val backdrop: Color,
    val title: Color,
)

val LocalLoginColors = staticCompositionLocalOf {
    LoginColors(LoginAccent, LoginBackdrop, LoginTitle)
}
