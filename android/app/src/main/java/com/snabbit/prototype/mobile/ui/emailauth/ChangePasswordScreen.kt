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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.snabbit.prototype.mobile.R
import com.snabbit.prototype.mobile.ui.theme.SnabbitSpacing

@Composable
fun ChangePasswordScreen(
    state: ChangePasswordUiState,
    onCurrentChanged: (String) -> Unit,
    onNewChanged: (String) -> Unit,
    onToggleCurrent: () -> Unit,
    onToggleNew: () -> Unit,
    onBlur: () -> Unit,
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
        Text(stringResource(R.string.email_auth_change_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.email_auth_change_subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(SnabbitSpacing.large))
        PasswordField(
            state.currentPassword, onCurrentChanged, state.currentVisible, onToggleCurrent,
            state.currentError, onBlur, R.string.email_auth_current_password, ImeAction.Next,
            onDone = {}, onNext = { focus.moveFocus(FocusDirection.Down) },
        )
        Spacer(Modifier.height(SnabbitSpacing.medium))
        PasswordField(
            state.newPassword, onNewChanged, state.newVisible, onToggleNew,
            state.newError, onBlur, R.string.email_auth_new_password, ImeAction.Done,
            onDone = { focus.clearFocus(); onSubmit() }, newPassword = true,
        )
        PasswordRulesChecklist(state.passwordValidation, includeIdentityRules = false)
        EmailAuthErrorText(state.error)
        Spacer(Modifier.height(SnabbitSpacing.large))
        Button(onClick = onSubmit, enabled = !state.isSubmitting, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            if (state.isSubmitting) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.height(24.dp), color = MaterialTheme.colorScheme.onPrimary)
            else Text(stringResource(R.string.email_auth_change_action))
        }
    }
}

