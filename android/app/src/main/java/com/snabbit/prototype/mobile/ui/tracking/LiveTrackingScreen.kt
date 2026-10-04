package com.snabbit.prototype.mobile.ui.tracking

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.snabbit.prototype.mobile.R
import com.snabbit.prototype.mobile.ui.common.DataErrorPanel
import com.snabbit.prototype.mobile.ui.components.StatusBadge
import com.snabbit.prototype.mobile.ui.theme.InProgressBlue
import com.snabbit.prototype.mobile.ui.theme.Mint
import com.snabbit.prototype.mobile.ui.theme.PendingAmber
import com.snabbit.prototype.mobile.ui.theme.SnabbitSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveTrackingScreen(
    state: LiveTrackingUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onCancelBooking: () -> Unit,
) {
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.screen_live_tracking)) }, navigationIcon = {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.action_back)) }
    }) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(SnabbitSpacing.standard),
            verticalArrangement = Arrangement.spacedBy(SnabbitSpacing.standard),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.tracking_worker_heading), style = MaterialTheme.typography.titleLarge)
                StatusBadge(
                    stringResource(state.connection.labelRes()),
                    if (state.connection == TrackingConnection.LIVE && !state.isStale) InProgressBlue else PendingAmber,
                )
            }
            Box(
                Modifier.fillMaxWidth().height(360.dp).background(Mint, MaterialTheme.shapes.large),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(SnabbitSpacing.small)) {
                    Icon(Icons.Outlined.LocationOn, stringResource(R.string.tracking_marker_description), tint = MaterialTheme.colorScheme.primary)
                    state.location?.let {
                        Text(stringResource(R.string.tracking_coordinates, it.latitude, it.longitude))
                        Text(stringResource(R.string.tracking_accuracy, it.accuracyMeters.toInt()))
                    } ?: Text(stringResource(R.string.tracking_waiting_location))
                }
            }
            if (state.isStale) Text(stringResource(R.string.tracking_stale), color = MaterialTheme.colorScheme.error)
            if (state.replayedLatest) Text(stringResource(R.string.tracking_replayed), color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (state.connection == TrackingConnection.RECONNECTING) {
                Text(stringResource(R.string.tracking_reconnecting_attempt, state.reconnectAttempt), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            state.error?.let { DataErrorPanel(it, onRetry) }
            OutlinedButton(onClick = onCancelBooking, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.tracking_cancel_booking))
            }
        }
    }
}

private fun TrackingConnection.labelRes() = when (this) {
    TrackingConnection.CONNECTING -> R.string.tracking_connecting
    TrackingConnection.LIVE -> R.string.tracking_live
    TrackingConnection.RECONNECTING -> R.string.tracking_reconnecting
}
