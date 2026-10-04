package com.snabbit.prototype.mobile.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.snabbit.prototype.mobile.R
import com.snabbit.prototype.mobile.ui.components.SnabbitButton
import com.snabbit.prototype.mobile.ui.theme.SnabbitSpacing

@Composable
fun DataErrorPanel(
    error: UiDataError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(SnabbitSpacing.large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SnabbitSpacing.medium),
    ) {
        Icon(
            imageVector = Icons.Outlined.CloudOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
        )
        Text(
            text = error.detail ?: stringResource(error.reason.messageResource()),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        SnabbitButton(text = stringResource(R.string.action_retry), onClick = onRetry)
    }
}

@Composable
fun EmptyStatePanel(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SnabbitSpacing.medium),
    ) {
        Icon(
            imageVector = Icons.Outlined.Inbox,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(
            description,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
    }
}

private fun UiDataErrorReason.messageResource(): Int = when (this) {
    UiDataErrorReason.UNAUTHORIZED -> R.string.data_error_unauthorized
    UiDataErrorReason.FORBIDDEN -> R.string.data_error_forbidden
    UiDataErrorReason.NOT_FOUND -> R.string.data_error_not_found
    UiDataErrorReason.CONFLICT -> R.string.data_error_conflict
    UiDataErrorReason.VALIDATION -> R.string.data_error_validation
    UiDataErrorReason.OFFLINE -> R.string.error_offline
    UiDataErrorReason.SERVER -> R.string.error_server
    UiDataErrorReason.UNKNOWN -> R.string.error_unknown
}
