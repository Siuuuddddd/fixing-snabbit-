package com.snabbit.prototype.mobile.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.snabbit.prototype.mobile.R
import com.snabbit.prototype.mobile.ui.components.SnabbitCard
import com.snabbit.prototype.mobile.ui.theme.SnabbitSpacing

@Composable
fun WorkerPickerScreen(state: WorkerPickerUiState, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(SnabbitSpacing.standard),
        verticalArrangement = Arrangement.spacedBy(SnabbitSpacing.medium),
    ) {
        Text(stringResource(R.string.worker_picker_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.worker_picker_subtitle), style = MaterialTheme.typography.bodyMedium)
        state.error?.let { Text(stringResource(it.messageRes()), color = MaterialTheme.colorScheme.error) }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(SnabbitSpacing.small)) {
            items(state.workers, key = { it.workerId }) { worker ->
                SnabbitCard(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(SnabbitSpacing.medium),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(worker.name, style = MaterialTheme.typography.titleMedium)
                            SuggestionChip(onClick = {}, label = { Text(worker.tier) })
                        }
                        Button(
                            onClick = { onSelect(worker.workerId) },
                            enabled = !state.isLoading,
                        ) {
                            if (state.isLoading && state.selectedWorkerId == worker.workerId) {
                                CircularProgressIndicator()
                            } else {
                                Text(stringResource(R.string.worker_picker_select))
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun WorkerPickerError.messageRes(): Int = when (this) {
    WorkerPickerError.EXPIRED -> R.string.worker_picker_expired
    WorkerPickerError.OFFLINE -> R.string.auth_error_offline
    WorkerPickerError.RATE_LIMITED -> R.string.auth_error_rate_limited
    WorkerPickerError.CANCELLED -> R.string.auth_error_cancelled
    WorkerPickerError.UNKNOWN -> R.string.auth_error_unknown
}
