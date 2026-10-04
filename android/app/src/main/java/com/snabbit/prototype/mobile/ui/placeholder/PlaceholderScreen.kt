package com.snabbit.prototype.mobile.ui.placeholder

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snabbit.prototype.mobile.R
import com.snabbit.prototype.mobile.ui.components.BrandLogo
import com.snabbit.prototype.mobile.ui.components.PlaceholderArtwork
import com.snabbit.prototype.mobile.ui.components.SnabbitButton
import com.snabbit.prototype.mobile.ui.theme.SnabbitSpacing
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun PlaceholderScreen(
    @StringRes titleRes: Int,
    viewModel: PlaceholderViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val title = stringResource(titleRes)
    Column(
        modifier = Modifier.fillMaxSize().padding(SnabbitSpacing.large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SnabbitSpacing.large, Alignment.CenterVertically),
    ) {
        BrandLogo()
        PlaceholderArtwork(contentDescription = stringResource(R.string.placeholder_description, title))
        Text(text = title)
        Text(text = stringResource(R.string.screen_not_implemented))
        SnabbitButton(
            text = stringResource(R.string.action_continue),
            onClick = { viewModel.onEvent(PlaceholderUiEvent.PrimaryActionClicked) },
        )
        if (state.interactionCount > 0) {
            Text(text = stringResource(R.string.placeholder_interaction_count, state.interactionCount))
        }
    }
}
