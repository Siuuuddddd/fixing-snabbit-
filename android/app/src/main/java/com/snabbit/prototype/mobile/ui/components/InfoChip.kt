package com.snabbit.prototype.mobile.ui.components

import androidx.compose.material3.AssistChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun InfoChip(text: String) {
    AssistChip(onClick = {}, label = { Text(text) })
}
