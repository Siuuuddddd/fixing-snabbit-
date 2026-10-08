package com.snabbit.prototype.mobile.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Single font-family switch for both app flavours.
 *
 * Google Sans is not distributed in the open Google Fonts catalogue. Android's
 * system sans-serif (Roboto on supported devices) is the legal bundled fallback.
 * Replace only this value with licensed Font resources if brand files are supplied.
 */
val SnabbitFontFamily: FontFamily = FontFamily.SansSerif

object SnabbitTextStyles {
    val ScreenTitle = TextStyle(
        fontFamily = SnabbitFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
    )
    val SectionHeading = TextStyle(
        fontFamily = SnabbitFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
    )
}

val SnabbitTypography = Typography(
    displayLarge = TextStyle(fontFamily = SnabbitFontFamily, fontWeight = FontWeight.Normal, fontSize = 57.sp),
    displayMedium = TextStyle(fontFamily = SnabbitFontFamily, fontWeight = FontWeight.Normal, fontSize = 45.sp),
    displaySmall = TextStyle(fontFamily = SnabbitFontFamily, fontWeight = FontWeight.Normal, fontSize = 36.sp),
    headlineLarge = TextStyle(fontFamily = SnabbitFontFamily, fontWeight = FontWeight.Bold, fontSize = 32.sp),
    headlineMedium = TextStyle(fontFamily = SnabbitFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 24.sp),
    headlineSmall = TextStyle(fontFamily = SnabbitFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
    titleLarge = TextStyle(fontFamily = SnabbitFontFamily, fontWeight = FontWeight.Medium, fontSize = 20.sp),
    titleMedium = TextStyle(fontFamily = SnabbitFontFamily, fontWeight = FontWeight.Medium, fontSize = 16.sp),
    titleSmall = TextStyle(fontFamily = SnabbitFontFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp),
    bodyLarge = TextStyle(fontFamily = SnabbitFontFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = SnabbitFontFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp),
    bodySmall = TextStyle(fontFamily = SnabbitFontFamily, fontWeight = FontWeight.Normal, fontSize = 12.sp),
    labelLarge = TextStyle(fontFamily = SnabbitFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    labelMedium = TextStyle(fontFamily = SnabbitFontFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp),
    labelSmall = TextStyle(fontFamily = SnabbitFontFamily, fontWeight = FontWeight.Medium, fontSize = 11.sp),
)
