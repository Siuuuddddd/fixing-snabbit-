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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.snabbit.prototype.mobile.R
import com.snabbit.prototype.mobile.ui.theme.LocalLoginColors

@Composable
fun PhoneOtpScreen(
    state: PhoneOtpUiState,
    onCodeChanged: (String) -> Unit,
    onVerify: () -> Unit,
    onResend: () -> Unit,
    onChangeNumber: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = LocalLoginColors.current.accent
    Column(
        modifier = modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.phone_otp_title), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.phone_otp_subtitle, state.maskedPhone),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = state.code,
            onValueChange = onCodeChanged,
            label = { Text(stringResource(R.string.phone_otp_code)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onVerify() }),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = onVerify,
            enabled = state.code.length == 6 && !state.isLoading,
            colors = ButtonDefaults.buttonColors(containerColor = accent),
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp).height(56.dp),
        ) {
            if (state.isLoading) CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary)
            else Text(stringResource(R.string.phone_otp_verify))
        }
        state.error?.let {
            Text(
                stringResource(it.messageRes()),
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        TextButton(onClick = onResend, enabled = state.resendSeconds == 0L && !state.isLoading) {
            Text(
                if (state.resendSeconds > 0) stringResource(R.string.phone_otp_resend_countdown, state.resendSeconds)
                else stringResource(R.string.phone_otp_resend),
            )
        }
        TextButton(onClick = onChangeNumber, enabled = !state.isLoading) {
            Text(stringResource(R.string.phone_otp_change_number))
        }
    }
}

private fun PhoneOtpError.messageRes(): Int = when (this) {
    PhoneOtpError.INVALID_OR_EXPIRED -> R.string.phone_otp_error_invalid
    PhoneOtpError.TOO_MANY_ATTEMPTS -> R.string.phone_otp_error_attempts
    PhoneOtpError.RATE_LIMITED -> R.string.auth_error_rate_limited
    PhoneOtpError.OFFLINE -> R.string.auth_error_offline
    PhoneOtpError.SERVER -> R.string.login_error_server
    PhoneOtpError.UNKNOWN -> R.string.auth_error_unknown
}
