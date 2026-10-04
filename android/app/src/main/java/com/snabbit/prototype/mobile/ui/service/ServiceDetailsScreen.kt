package com.snabbit.prototype.mobile.ui.service

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.snabbit.prototype.mobile.R
import com.snabbit.prototype.mobile.ui.common.DataErrorPanel
import com.snabbit.prototype.mobile.ui.common.formatInrMinor
import com.snabbit.prototype.mobile.ui.components.InfoChip
import com.snabbit.prototype.mobile.ui.components.SnabbitButton
import com.snabbit.prototype.mobile.ui.theme.Mint
import com.snabbit.prototype.mobile.ui.theme.SnabbitSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceDetailsScreen(
    state: ServiceDetailsUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onDurationSelected: (Int) -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.screen_service_details)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
        bottomBar = {
            state.service?.let { service ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(SnabbitSpacing.standard),
                ) {
                    Text(
                        text = stringResource(
                            R.string.service_total_price,
                            formatInrMinor(service.basePricePerHourMinor * state.selectedDurationHours),
                            state.selectedDurationHours,
                        ),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Spacer(Modifier.height(SnabbitSpacing.small))
                    SnabbitButton(text = stringResource(R.string.action_continue), onClick = onContinue)
                }
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when {
                state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.error != null && state.service == null -> DataErrorPanel(
                    error = state.error,
                    onRetry = onRetry,
                    modifier = Modifier.align(Alignment.Center),
                )
                state.service != null -> ServiceDetailsContent(state, onDurationSelected, onRetry)
            }
        }
    }
}

@Composable
private fun ServiceDetailsContent(
    state: ServiceDetailsUiState,
    onDurationSelected: (Int) -> Unit,
    onRetry: () -> Unit,
) {
    val service = requireNotNull(state.service)
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(SnabbitSpacing.standard),
        verticalArrangement = Arrangement.spacedBy(SnabbitSpacing.standard),
    ) {
        state.error?.let { error ->
            DataErrorPanel(error = error, onRetry = onRetry)
        }
        Box(
            modifier = Modifier.fillMaxWidth().height(220.dp).background(Mint, MaterialTheme.shapes.large),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.CleaningServices,
                contentDescription = stringResource(R.string.service_artwork_description, service.displayName),
                modifier = Modifier.size(96.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Text(service.displayName, style = MaterialTheme.typography.headlineMedium)
        Text(
            text = stringResource(R.string.service_details_description),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(stringResource(R.string.service_included_title), style = MaterialTheme.typography.titleLarge)
        stringArrayResource(R.array.service_included_items).forEach { item ->
            Row(horizontalArrangement = Arrangement.spacedBy(SnabbitSpacing.small)) {
                Icon(
                    Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(item, style = MaterialTheme.typography.bodyLarge)
            }
        }
        Text(stringResource(R.string.service_duration_title), style = MaterialTheme.typography.titleLarge)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SnabbitSpacing.small),
        ) {
            (1..4).forEach { hours ->
                Box(modifier = Modifier.weight(1f)) {
                    InfoChip(
                        text = stringResource(
                            if (hours == state.selectedDurationHours) R.string.duration_selected else R.string.duration_hours,
                            hours,
                        ),
                        onClick = { onDurationSelected(hours) },
                        selected = hours == state.selectedDurationHours,
                    )
                }
            }
        }
        Text(
            stringResource(R.string.service_pricing_note),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(88.dp))
    }
}
