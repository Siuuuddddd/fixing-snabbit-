package com.snabbit.prototype.mobile.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

private val ColorWhite = androidx.compose.ui.graphics.Color.White

private val LightColors = lightColorScheme(
    primary = SnabbitPink,
    primaryContainer = ScheduleSelectedFill,
    secondary = Saffron,
    tertiary = ConfirmedGreen,
    background = WarmWhite,
    surface = ColorWhite,
    onPrimary = ColorWhite,
    onBackground = Charcoal,
    onSurface = Charcoal,
    error = ErrorRed,
)

private val DarkColors = darkColorScheme(
    primary = SnabbitPinkDark,
    primaryContainer = ScheduleSelectedFillDark,
    secondary = Saffron,
    tertiary = Mint,
    onPrimary = Charcoal,
    error = ErrorRed,
)

@Composable
fun SnabbitTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val loginColors = if (darkTheme) {
        LoginColors(LoginAccentDark, LoginBackdropDark, LoginTitleDark)
    } else {
        LoginColors(LoginAccent, LoginBackdrop, LoginTitle)
    }
    CompositionLocalProvider(LocalLoginColors provides loginColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = SnabbitTypography,
            shapes = SnabbitShapes,
            content = content,
        )
    }
}
