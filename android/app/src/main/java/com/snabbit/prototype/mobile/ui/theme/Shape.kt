package com.snabbit.prototype.mobile.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val SnabbitShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
)

object SnabbitSpacing {
    val extraSmall = 4.dp
    val small = 8.dp
    val medium = 12.dp
    val standard = 16.dp
    val large = 24.dp
    val extraLarge = 32.dp
}
