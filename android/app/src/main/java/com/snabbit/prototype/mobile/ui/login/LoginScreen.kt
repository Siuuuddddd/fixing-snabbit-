package com.snabbit.prototype.mobile.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.snabbit.prototype.mobile.R
import com.snabbit.prototype.mobile.ui.components.BrandLogo
import com.snabbit.prototype.mobile.ui.theme.SnabbitSpacing

@Composable
fun LoginScreen(
    state: LoginUiState,
    onContinueWithGoogle: () -> Unit,
    onAddGoogleAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(SnabbitSpacing.large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        BrandLogo()
        Spacer(Modifier.height(SnabbitSpacing.large))
        Text(
            text = stringResource(R.string.google_login_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(SnabbitSpacing.small))
        Text(
            text = stringResource(R.string.google_login_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(SnabbitSpacing.extraLarge))
        Button(
            onClick = onContinueWithGoogle,
            enabled = state.isConfigured && !state.isLoading,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.height(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                )
            } else {
                Text(stringResource(R.string.continue_with_google))
            }
        }
        state.error?.let { error ->
            Spacer(Modifier.height(SnabbitSpacing.medium))
            Text(
                text = stringResource(error.messageRes()),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            if (error == LoginError.NO_GOOGLE_ACCOUNT) {
                TextButton(onClick = onAddGoogleAccount) {
                    Text(stringResource(R.string.add_google_account))
                }
            }
        }
        Spacer(Modifier.height(SnabbitSpacing.large))
        Text(
            text = stringResource(R.string.google_login_terms),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private fun LoginError.messageRes(): Int = when (this) {
    LoginError.NOT_CONFIGURED -> R.string.auth_error_not_configured
    LoginError.NO_GOOGLE_ACCOUNT -> R.string.auth_error_no_google_account
    LoginError.OFFLINE -> R.string.auth_error_offline
    LoginError.PLAY_SERVICES_UNAVAILABLE -> R.string.auth_error_play_services
    LoginError.GOOGLE_CONFIGURATION -> R.string.auth_error_google_configuration
    LoginError.INVALID_GOOGLE_TOKEN -> R.string.auth_error_invalid_google_token
    LoginError.EMAIL_NOT_VERIFIED -> R.string.auth_error_email_not_verified
    LoginError.WORKER_NOT_REGISTERED -> R.string.auth_error_worker_not_registered
    LoginError.ACCOUNT_DISABLED -> R.string.auth_error_account_disabled
    LoginError.ACCOUNT_LINK_CONFLICT -> R.string.auth_error_account_link_conflict
    LoginError.RATE_LIMITED -> R.string.auth_error_rate_limited
    LoginError.UNKNOWN -> R.string.auth_error_unknown
}
