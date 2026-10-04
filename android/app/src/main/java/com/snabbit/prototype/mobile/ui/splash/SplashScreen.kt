package com.snabbit.prototype.mobile.ui.splash

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.snabbit.prototype.mobile.R
import com.snabbit.prototype.mobile.ui.components.BrandLogo
import com.snabbit.prototype.mobile.ui.theme.SnabbitPink
import com.snabbit.prototype.mobile.ui.theme.SnabbitPinkDark
import com.snabbit.prototype.mobile.ui.theme.SnabbitSpacing

@Composable
fun SplashScreen(modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 600),
        label = "splash_content_alpha",
    )
    LaunchedEffect(Unit) { visible = true }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(SnabbitPink, SnabbitPinkDark))),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.alpha(alpha).padding(SnabbitSpacing.extraLarge),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(SnabbitSpacing.small),
        ) {
            BrandLogo(color = MaterialTheme.colorScheme.onPrimary)
            Text(
                text = stringResource(R.string.splash_tagline),
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
        }
    }
}
