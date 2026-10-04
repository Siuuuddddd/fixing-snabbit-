package com.snabbit.prototype.mobile.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val ColorWhite = androidx.compose.ui.graphics.Color.White

private val LightColors = lightColorScheme(
    primary = SnabbitPink,
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
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = SnabbitTypography,
        shapes = SnabbitShapes,
        content = content,
    )
}
