package com.snabbit.prototype.mobile.ui.completed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.snabbit.prototype.mobile.R
import com.snabbit.prototype.mobile.ui.common.DataErrorPanel
import com.snabbit.prototype.mobile.ui.common.formatInrMinor
import com.snabbit.prototype.mobile.ui.components.SnabbitButton
import com.snabbit.prototype.mobile.ui.theme.ConfirmedGreen
import com.snabbit.prototype.mobile.ui.theme.SnabbitSpacing

@Composable
fun BookingCompletedScreen(
    state: BookingCompletedUiState,
    onRetry: () -> Unit,
    onStarsSelected: (Int) -> Unit,
    onCommentChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onDone: () -> Unit,
) {
    Scaffold { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(SnabbitSpacing.large),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(SnabbitSpacing.standard, Alignment.CenterVertically),
        ) {
            Icon(Icons.Outlined.CheckCircle, null, tint = ConfirmedGreen, modifier = Modifier.padding(SnabbitSpacing.small))
            Text(stringResource(R.string.completed_title), style = MaterialTheme.typography.headlineMedium)
            state.booking?.let { Text(stringResource(R.string.completed_paid, formatInrMinor(it.quotedTotalMinor))) }
            if (state.isLoading) CircularProgressIndicator()
            state.error?.let { DataErrorPanel(it, onRetry) }
            if (state.rating == null) {
                Text(stringResource(R.string.completed_rate_prompt), style = MaterialTheme.typography.titleLarge)
                Row {
                    (1..5).forEach { star ->
                        IconButton(onClick = { onStarsSelected(star) }) {
                            Icon(
                                if (star <= state.stars) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                                stringResource(R.string.completed_star_description, star),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                if (state.validationError) Text(stringResource(R.string.completed_rating_required), color = MaterialTheme.colorScheme.error)
                OutlinedTextField(
                    value = state.comment,
                    onValueChange = onCommentChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.completed_comment_label)) },
                    minLines = 3,
                )
                SnabbitButton(
                    stringResource(R.string.completed_submit_rating),
                    onSubmit,
                    enabled = !state.isLoading && state.booking != null,
                    loading = state.isSubmitting,
                )
            } else {
                Text(stringResource(R.string.completed_rating_saved), color = ConfirmedGreen)
                SnabbitButton(stringResource(R.string.completed_done), onDone)
            }
        }
    }
}
