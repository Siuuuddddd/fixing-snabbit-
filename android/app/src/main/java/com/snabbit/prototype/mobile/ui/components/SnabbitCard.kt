package com.snabbit.prototype.mobile.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.snabbit.prototype.mobile.ui.theme.SnabbitSpacing

@Composable
fun SnabbitCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(modifier = modifier, elevation = CardDefaults.cardElevation(defaultElevation = SnabbitSpacing.extraSmall)) {
        Column(modifier = Modifier.padding(SnabbitSpacing.standard), content = content)
    }
}
