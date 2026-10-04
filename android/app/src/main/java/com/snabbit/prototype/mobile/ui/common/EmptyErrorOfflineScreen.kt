package com.snabbit.prototype.mobile.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.snabbit.prototype.mobile.R
import com.snabbit.prototype.mobile.ui.theme.SnabbitSpacing

@Composable
fun EmptyErrorOfflineScreen(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(SnabbitSpacing.standard),
        verticalArrangement = Arrangement.spacedBy(SnabbitSpacing.large),
    ) {
        item { Text(stringResource(R.string.states_title), style = MaterialTheme.typography.headlineMedium) }
        item { EmptyStatePanel(stringResource(R.string.states_empty_title), stringResource(R.string.states_empty_body)) }
        item { DataErrorPanel(UiDataError(UiDataErrorReason.OFFLINE), onRetry) }
        item { DataErrorPanel(UiDataError(UiDataErrorReason.SERVER), onRetry) }
    }
}
