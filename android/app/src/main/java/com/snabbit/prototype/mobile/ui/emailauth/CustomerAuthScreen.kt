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
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.autofill.contentType
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.snabbit.prototype.mobile.R
import com.snabbit.prototype.mobile.domain.auth.AuthNotice
import com.snabbit.prototype.mobile.ui.components.BrandLogo
import com.snabbit.prototype.mobile.ui.login.LoginError
import com.snabbit.prototype.mobile.ui.login.LoginUiState
import com.snabbit.prototype.mobile.ui.theme.SnabbitSpacing

@Composable
fun CustomerAuthScreen(
    state: AuthUiState,
    googleState: LoginUiState,
    onSelectTab: (AuthTab) -> Unit,
    onLoginEmailChanged: (String) -> Unit,
    onLoginPasswordChanged: (String) -> Unit,
    onSignUpNameChanged: (String) -> Unit,
    onSignUpEmailChanged: (String) -> Unit,
    onSignUpPasswordChanged: (String) -> Unit,
    onTermsChanged: (Boolean) -> Unit,
    onTogglePassword: () -> Unit,
    onEmailBlur: () -> Unit,
    onNameBlur: () -> Unit,
    onPasswordBlur: () -> Unit,
    onSubmit: () -> Unit,
    onForgotPassword: () -> Unit,
    onContinueWithGoogle: () -> Unit,
    onAddGoogleAccount: () -> Unit,
    onTerms: () -> Unit,
    onPrivacy: () -> Unit,
    onDismissNotice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focus = LocalFocusManager.current
    Column(
        modifier = modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding()
            .verticalScroll(rememberScrollState()).padding(SnabbitSpacing.large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        BrandLogo()
        Spacer(Modifier.height(SnabbitSpacing.large))
        Text(stringResource(R.string.email_auth_welcome), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Text(stringResource(R.string.email_auth_subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(SnabbitSpacing.large))
        state.notice?.let { notice ->
            val message = when (notice) {
                AuthNotice.PASSWORD_UPDATED -> R.string.email_auth_password_updated
                AuthNotice.SESSION_REVOKED -> R.string.email_auth_error_session_revoked
            }
            TextButton(onClick = onDismissNotice) {
                Text(
                    stringResource(message),
                    color = if (notice == AuthNotice.SESSION_REVOKED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
        }
        TabRow(selectedTabIndex = state.tab.ordinal) {
            AuthTab.entries.forEach { tab ->
                Tab(
                    selected = state.tab == tab,
                    onClick = { onSelectTab(tab) },
                    text = { Text(stringResource(if (tab == AuthTab.LOGIN) R.string.email_auth_login_tab else R.string.email_auth_signup_tab)) },
                    enabled = !state.isSubmitting,
                )
            }
        }
        Spacer(Modifier.height(SnabbitSpacing.large))
        if (state.tab == AuthTab.LOGIN) {
            EmailField(
                state.loginEmail, onLoginEmailChanged, state.emailError, onEmailBlur, ImeAction.Next,
                onNext = { focus.moveFocus(FocusDirection.Down) },
            )
            Spacer(Modifier.height(SnabbitSpacing.medium))
            PasswordField(
                state.loginPassword, onLoginPasswordChanged, state.passwordVisible, onTogglePassword,
                state.passwordError, onPasswordBlur, R.string.email_auth_password, ImeAction.Done,
                onDone = { focus.clearFocus(); onSubmit() },
            )
            TextButton(onClick = onForgotPassword, enabled = !state.isSubmitting, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.email_auth_forgot_password))
            }
        } else {
            OutlinedTextField(
                value = state.signUpName,
                onValueChange = onSignUpNameChanged,
                modifier = Modifier.fillMaxWidth().contentType(ContentType.PersonFullName).onFocusChanged { if (!it.isFocused && state.signUpName.isNotBlank()) onNameBlur() },
                label = { Text(stringResource(R.string.email_auth_full_name)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
                singleLine = true,
                isError = state.nameError,
                supportingText = if (state.nameError) ({ Text(stringResource(R.string.email_auth_invalid_name)) }) else null,
            )
            Spacer(Modifier.height(SnabbitSpacing.medium))
            EmailField(
                state.signUpEmail, onSignUpEmailChanged, state.emailError, onEmailBlur, ImeAction.Next,
                onNext = { focus.moveFocus(FocusDirection.Down) },
            )
            Spacer(Modifier.height(SnabbitSpacing.medium))
            PasswordField(
                state.signUpPassword, onSignUpPasswordChanged, state.passwordVisible, onTogglePassword,
                state.passwordError, onPasswordBlur, R.string.email_auth_password, ImeAction.Done,
                onDone = { focus.clearFocus(); onSubmit() }, newPassword = true,
            )
            PasswordRulesChecklist(state.passwordValidation)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = state.termsAccepted, onCheckedChange = onTermsChanged, enabled = !state.isSubmitting)
                Column {
                    Text(stringResource(R.string.email_auth_accept_terms), style = MaterialTheme.typography.bodySmall)
                    Row {
                        TextButton(onClick = onTerms) { Text(stringResource(R.string.email_auth_terms)) }
                        TextButton(onClick = onPrivacy) { Text(stringResource(R.string.email_auth_privacy)) }
                    }
                }
            }
            if (state.termsError) Text(stringResource(R.string.email_auth_terms_required), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        EmailAuthErrorText(state.error)
        Spacer(Modifier.height(SnabbitSpacing.medium))
        Button(onClick = { focus.clearFocus(); onSubmit() }, enabled = !state.isSubmitting, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            if (state.isSubmitting) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.height(24.dp), color = MaterialTheme.colorScheme.onPrimary)
            else Text(stringResource(if (state.tab == AuthTab.LOGIN) R.string.email_auth_login_action else R.string.email_auth_signup_action))
        }
        Spacer(Modifier.height(SnabbitSpacing.large))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SnabbitSpacing.medium)) {
            HorizontalDivider(Modifier.weight(1f)); Text(stringResource(R.string.email_auth_or)); HorizontalDivider(Modifier.weight(1f))
        }
        Spacer(Modifier.height(SnabbitSpacing.medium))
        Button(
            onClick = onContinueWithGoogle,
            enabled = googleState.isConfigured && !googleState.isLoading && !state.isSubmitting,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            if (googleState.isLoading) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.height(24.dp), color = MaterialTheme.colorScheme.onPrimary)
            else Text(stringResource(R.string.continue_with_google))
        }
        googleState.error?.let { error ->
            Spacer(Modifier.height(SnabbitSpacing.small))
            Text(stringResource(error.googleMessageRes()), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
            if (error == LoginError.NO_GOOGLE_ACCOUNT) TextButton(onClick = onAddGoogleAccount) { Text(stringResource(R.string.add_google_account)) }
        }
    }
}

private fun LoginError.googleMessageRes(): Int = when (this) {
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

