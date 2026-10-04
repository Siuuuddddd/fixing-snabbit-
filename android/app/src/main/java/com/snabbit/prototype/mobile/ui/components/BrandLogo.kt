package com.snabbit.prototype.mobile.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.snabbit.prototype.mobile.R

@Composable
fun BrandLogo(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.brand_name),
        modifier = modifier,
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.headlineLarge,
    )
}
