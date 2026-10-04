package com.snabbit.prototype.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun StatusBadge(text: String, color: Color) {
    Text(
        text = text,
        modifier = Modifier.background(color.copy(alpha = 0.14f), MaterialTheme.shapes.small).padding(horizontal = 12.dp, vertical = 6.dp),
        color = color,
        style = MaterialTheme.typography.bodyMedium,
    )
}
