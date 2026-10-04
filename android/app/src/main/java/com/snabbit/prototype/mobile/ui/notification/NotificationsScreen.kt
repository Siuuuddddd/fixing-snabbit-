package com.snabbit.prototype.mobile.ui.notification

import android.Manifest
import android.os.Build
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.snabbit.prototype.mobile.R
import com.snabbit.prototype.mobile.domain.notification.NotificationItem
import com.snabbit.prototype.mobile.ui.common.DataErrorPanel
import com.snabbit.prototype.mobile.ui.common.EmptyStatePanel
import com.snabbit.prototype.mobile.ui.common.LoadingStatePanel
import com.snabbit.prototype.mobile.ui.components.SnabbitCard
import com.snabbit.prototype.mobile.ui.theme.SnabbitSpacing
import androidx.core.content.ContextCompat

@Composable
fun NotificationsScreen(
    state: NotificationsUiState,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onFilterSelected: (NotificationFilter) -> Unit,
    onNotificationSelected: (Long) -> Unit,
    onRetryPush: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var permissionGranted by remember {
        mutableStateOf(Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permissionGranted = it }
    PullToRefreshBox(state.isRefreshing, onRefresh, modifier.fillMaxSize()) {
        when {
            state.isLoading -> LoadingStatePanel(stringResource(R.string.notifications_loading), Modifier.align(Alignment.Center))
            state.error != null && state.notifications.isEmpty() -> DataErrorPanel(state.error, onRetry, Modifier.align(Alignment.Center))
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(SnabbitSpacing.standard),
                verticalArrangement = Arrangement.spacedBy(SnabbitSpacing.medium),
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.action_back)) }
                        Text(stringResource(R.string.notifications_title), style = MaterialTheme.typography.headlineMedium)
                    }
                }
                item {
                    PushStatusCard(state.pushState, onRetryPush)
                }
                if (Build.VERSION.SDK_INT >= 33 && !permissionGranted) {
                    item { PermissionCard { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) } }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(SnabbitSpacing.small)) {
                        NotificationFilter.entries.forEach { filter ->
                            AssistChip(onClick = { onFilterSelected(filter) }, label = { Text(filter.label()) }, leadingIcon = if (filter == state.filter) ({ Icon(Icons.Outlined.Check, null) }) else null)
                        }
                    }
                }
                state.error?.let { item { DataErrorPanel(it, onRetry) } }
                if (state.visibleNotifications.isEmpty()) {
                    item { EmptyStatePanel(stringResource(R.string.notifications_empty_title), stringResource(R.string.notifications_empty_body)) }
                } else {
                    items(state.visibleNotifications, key = NotificationItem::id) { item -> NotificationCard(item) { onNotificationSelected(item.id) } }
                }
            }
        }
    }
}

@Composable
private fun PushStatusCard(state: PushRegistrationState, onRetry: () -> Unit) {
    if (state == PushRegistrationState.REGISTERED) return
    SnabbitCard(Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(SnabbitSpacing.medium), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.NotificationsOff, contentDescription = null)
            Column(Modifier.weight(1f)) {
                Text(
                    when (state) {
                        PushRegistrationState.CHECKING -> stringResource(R.string.notifications_push_checking)
                        PushRegistrationState.NOT_CONFIGURED -> stringResource(R.string.notifications_push_not_configured)
                        PushRegistrationState.FAILED -> stringResource(R.string.notifications_push_failed)
                        PushRegistrationState.REGISTERED -> ""
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (state == PushRegistrationState.FAILED) Button(onRetry) { Text(stringResource(R.string.action_retry)) }
            }
        }
    }
}

@Composable
private fun PermissionCard(onEnable: () -> Unit) {
    SnabbitCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.notifications_permission_body), style = MaterialTheme.typography.bodyMedium)
        Button(onEnable, Modifier.padding(top = SnabbitSpacing.small)) { Text(stringResource(R.string.notifications_enable_permission)) }
    }
}

@Composable
private fun NotificationCard(item: NotificationItem, onClick: () -> Unit) {
    androidx.compose.material3.Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(SnabbitSpacing.standard), horizontalArrangement = Arrangement.spacedBy(SnabbitSpacing.medium)) {
            Icon(Icons.Outlined.Notifications, contentDescription = null, tint = if (item.critical) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium)
                Text(item.body, style = MaterialTheme.typography.bodyMedium)
                Text(item.createdAt, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (item.readAt == null) Text(stringResource(R.string.notifications_unread), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun NotificationFilter.label(): String = stringResource(
    when (this) {
        NotificationFilter.ALL -> R.string.notifications_filter_all
        NotificationFilter.BOOKINGS -> R.string.notifications_filter_bookings
        NotificationFilter.PAYMENTS -> R.string.notifications_filter_payments
        NotificationFilter.OFFERS -> R.string.notifications_filter_offers
    },
)
