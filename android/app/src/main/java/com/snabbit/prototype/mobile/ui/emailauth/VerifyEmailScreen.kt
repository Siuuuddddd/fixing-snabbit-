package com.snabbit.prototype.mobile.ui.emailauth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.snabbit.prototype.mobile.R
import com.snabbit.prototype.mobile.ui.components.BrandLogo
import com.snabbit.prototype.mobile.ui.theme.SnabbitSpacing

@Composable
fun VerifyEmailScreen(
    state: VerifyEmailUiState,
    onCodeChanged: (String) -> Unit,
    onVerify: () -> Unit,
    onResend: () -> Unit,
    onChangeEmail: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focus = LocalFocusManager.current
    Column(
        modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding()
            .verticalScroll(rememberScrollState()).padding(SnabbitSpacing.large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        BrandLogo()
        Spacer(Modifier.height(SnabbitSpacing.large))
        Text(stringResource(R.string.email_auth_verify_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.email_auth_verify_subtitle, state.email), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(SnabbitSpacing.large))
        OutlinedTextField(
            value = state.code,
            onValueChange = onCodeChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.email_auth_code)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focus.clearFocus(); onVerify() }),
            singleLine = true,
            isError = state.error?.type in setOf(EmailAuthUiError.INVALID_OR_EXPIRED_CODE, EmailAuthUiError.TOO_MANY_ATTEMPTS),
        )
        EmailAuthErrorText(state.error)
        Spacer(Modifier.height(SnabbitSpacing.medium))
        Button(onClick = onVerify, enabled = !state.isSubmitting && state.code.length == 6, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            if (state.isSubmitting) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.height(24.dp), color = MaterialTheme.colorScheme.onPrimary)
            else Text(stringResource(R.string.email_auth_verify_action))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onChangeEmail, enabled = !state.isSubmitting) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = null)
                Text(stringResource(R.string.email_auth_change_email))
            }
            TextButton(onClick = onResend, enabled = !state.isSubmitting && state.resendSeconds == 0L) {
                Text(if (state.resendSeconds > 0) stringResource(R.string.email_auth_resend_countdown, state.resendSeconds) else stringResource(R.string.email_auth_resend))
            }
        }
    }
}

