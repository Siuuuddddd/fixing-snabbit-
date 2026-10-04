package com.snabbit.prototype.mobile.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Stars
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import com.snabbit.prototype.mobile.R
import com.snabbit.prototype.mobile.ui.common.DataErrorPanel
import com.snabbit.prototype.mobile.ui.common.LoadingStatePanel
import com.snabbit.prototype.mobile.ui.components.SnabbitCard
import com.snabbit.prototype.mobile.ui.theme.SnabbitSpacing

@Composable
fun ProfileScreen(state: ProfileUiState, onRefresh: () -> Unit, onRetry: () -> Unit, onSignOut: () -> Unit, modifier: Modifier = Modifier) {
    PullToRefreshBox(state.isRefreshing, onRefresh, modifier.fillMaxSize()) {
        when {
            state.isLoading -> LoadingStatePanel(stringResource(R.string.profile_loading), Modifier.align(Alignment.Center))
            state.error != null && state.profile == null -> DataErrorPanel(state.error, onRetry, Modifier.align(Alignment.Center))
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(SnabbitSpacing.standard),
                verticalArrangement = Arrangement.spacedBy(SnabbitSpacing.medium),
            ) {
                item { Text(stringResource(R.string.profile_title), style = MaterialTheme.typography.headlineMedium) }
                state.profile?.let { profile ->
                    item {
                        SnabbitCard(Modifier.fillMaxWidth()) {
                            Icon(Icons.Outlined.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(stringResource(if (profile.role == "WORKER") R.string.profile_worker_account else R.string.profile_customer_account), style = MaterialTheme.typography.titleLarge)
                            Text(stringResource(R.string.profile_subject, profile.subjectId.take(12)), style = MaterialTheme.typography.bodySmall)
                            Text(stringResource(R.string.profile_identity_gap), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (profile.savedAddressCount != null) item { ProfileRow(Icons.Outlined.Home, stringResource(R.string.profile_addresses), pluralStringResource(R.plurals.profile_address_count, profile.savedAddressCount, profile.savedAddressCount)) }
                    if (profile.hasHouseholdPreferences != null) item { ProfileRow(Icons.Outlined.Language, stringResource(R.string.profile_preferences), stringResource(if (profile.hasHouseholdPreferences) R.string.profile_configured else R.string.profile_not_configured)) }
                    if (profile.plusActive != null) {
                        val passes = (profile.plusPasses ?: 0).toInt()
                        item { ProfileRow(Icons.Outlined.Stars, stringResource(R.string.profile_plus), if (profile.plusActive) pluralStringResource(R.plurals.profile_plus_active, passes, passes) else stringResource(R.string.profile_plus_inactive)) }
                    }
                }
                state.error?.let { item { DataErrorPanel(it, onRetry) } }
                item {
                    Button(
                        onClick = onSignOut,
                        enabled = !state.isSigningOut,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(if (state.isSigningOut) R.string.profile_signing_out else R.string.profile_sign_out)) }
                }
            }
        }
    }
}

@Composable
private fun ProfileRow(icon: ImageVector, title: String, value: String) {
    SnabbitCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(SnabbitSpacing.medium), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(value, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}
