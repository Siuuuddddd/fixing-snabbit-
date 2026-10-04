package com.snabbit.prototype.mobile.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.snabbit.prototype.mobile.R
import com.snabbit.prototype.mobile.domain.catalog.ServiceOffering
import com.snabbit.prototype.mobile.ui.common.DataErrorPanel
import com.snabbit.prototype.mobile.ui.common.EmptyStatePanel
import com.snabbit.prototype.mobile.ui.common.formatInrMinor
import com.snabbit.prototype.mobile.ui.components.SnabbitCard
import com.snabbit.prototype.mobile.ui.theme.Mint
import com.snabbit.prototype.mobile.ui.theme.Peach
import com.snabbit.prototype.mobile.ui.theme.SnabbitSpacing

@Composable
fun HomeScreen(
    state: HomeUiState,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onServiceSelected: (String) -> Unit,
    onAddressesSelected: () -> Unit,
    onNotificationsSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize(),
    ) {
        when {
            state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            state.error != null && state.services.isEmpty() -> DataErrorPanel(
                error = state.error,
                onRetry = onRetry,
                modifier = Modifier.align(Alignment.Center),
            )
            else -> HomeContent(
                state = state,
                onServiceSelected = onServiceSelected,
                onAddressesSelected = onAddressesSelected,
                onNotificationsSelected = onNotificationsSelected,
                onRetry = onRetry,
            )
        }
    }
}

@Composable
private fun HomeContent(
    state: HomeUiState,
    onServiceSelected: (String) -> Unit,
    onAddressesSelected: () -> Unit,
    onNotificationsSelected: () -> Unit,
    onRetry: () -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(SnabbitSpacing.standard),
        horizontalArrangement = Arrangement.spacedBy(SnabbitSpacing.medium),
        verticalArrangement = Arrangement.spacedBy(SnabbitSpacing.medium),
    ) {
        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
            HomeHeader(onAddressesSelected, onNotificationsSelected)
        }
        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
            HeroPanel()
        }
        state.error?.let { error ->
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                DataErrorPanel(error = error, onRetry = onRetry)
            }
        }
        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
            Text(
                text = stringResource(R.string.home_services_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = SnabbitSpacing.small),
            )
        }
        if (state.services.isEmpty()) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                EmptyStatePanel(
                    title = stringResource(R.string.home_empty_title),
                    description = stringResource(R.string.home_empty_description),
                )
            }
        } else {
            items(state.services, key = ServiceOffering::id) { service ->
                ServiceCard(service = service, onClick = { onServiceSelected(service.id) })
            }
        }
    }
}

@Composable
private fun HomeHeader(onAddressesSelected: () -> Unit, onNotificationsSelected: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f).clickable(onClick = onAddressesSelected),
        ) {
            Text(stringResource(R.string.home_greeting), style = MaterialTheme.typography.headlineMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.LocationOn,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    stringResource(R.string.home_location_prompt),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        IconButton(onClick = onNotificationsSelected) {
            Icon(Icons.Outlined.Notifications, contentDescription = stringResource(R.string.notifications_description))
        }
    }
}

@Composable
private fun HeroPanel() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.horizontalGradient(listOf(Peach, Mint)),
                MaterialTheme.shapes.large,
            )
            .padding(SnabbitSpacing.large),
        verticalArrangement = Arrangement.spacedBy(SnabbitSpacing.small),
    ) {
        Text(stringResource(R.string.home_hero_title), style = MaterialTheme.typography.headlineMedium)
        Text(
            stringResource(R.string.home_hero_description),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(SnabbitSpacing.small))
        Row(horizontalArrangement = Arrangement.spacedBy(SnabbitSpacing.small)) {
            Icon(Icons.Outlined.AccessTime, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.home_hours), fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ServiceCard(service: ServiceOffering, onClick: () -> Unit) {
    SnabbitCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
                .background(Mint, MaterialTheme.shapes.medium),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.CleaningServices,
                contentDescription = stringResource(R.string.service_artwork_description, service.displayName),
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(SnabbitSpacing.medium))
        Text(service.displayName, style = MaterialTheme.typography.titleLarge)
        Text(
            stringResource(R.string.service_from_price, formatInrMinor(service.basePricePerHourMinor)),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
