package com.snabbit.prototype.mobile.ui.emailauth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.snabbit.prototype.mobile.R
import com.snabbit.prototype.mobile.ui.theme.SnabbitSpacing

@Composable
fun ForgotPasswordScreen(
    state: ForgotPasswordUiState,
    onEmailChanged: (String) -> Unit,
    onCodeChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onTogglePassword: () -> Unit,
    onEmailBlur: () -> Unit,
    onPasswordBlur: () -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focus = LocalFocusManager.current
    Column(
        modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding()
            .verticalScroll(rememberScrollState()).padding(SnabbitSpacing.large),
        verticalArrangement = Arrangement.Center,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.Start)) {
            Icon(Icons.Outlined.ArrowBack, stringResource(R.string.email_auth_back))
        }
        Text(stringResource(R.string.email_auth_forgot_title), style = MaterialTheme.typography.headlineMedium)
        Text(
            stringResource(if (state.step == ForgotPasswordStep.EMAIL) R.string.email_auth_forgot_subtitle else R.string.email_auth_reset_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(SnabbitSpacing.large))
        if (state.step == ForgotPasswordStep.EMAIL) {
            EmailField(state.email, onEmailChanged, state.emailError, onEmailBlur, ImeAction.Done, onDone = { focus.clearFocus(); onSubmit() })
        } else {
            OutlinedTextField(
                value = state.code,
                onValueChange = onCodeChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.email_auth_code)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Next),
                singleLine = true,
                isError = state.codeError,
            )
            Spacer(Modifier.height(SnabbitSpacing.medium))
            PasswordField(
                state.newPassword, onPasswordChanged, state.passwordVisible, onTogglePassword,
                state.passwordError, onPasswordBlur, R.string.email_auth_new_password, ImeAction.Done,
                onDone = { focus.clearFocus(); onSubmit() }, newPassword = true,
            )
            PasswordRulesChecklist(state.passwordValidation, includeIdentityRules = false)
        }
        EmailAuthErrorText(state.error)
        Spacer(Modifier.height(SnabbitSpacing.large))
        Button(onClick = onSubmit, enabled = !state.isSubmitting, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            if (state.isSubmitting) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.height(24.dp), color = MaterialTheme.colorScheme.onPrimary)
            else Text(stringResource(if (state.step == ForgotPasswordStep.EMAIL) R.string.email_auth_send_reset else R.string.email_auth_reset_action))
        }
    }
}

