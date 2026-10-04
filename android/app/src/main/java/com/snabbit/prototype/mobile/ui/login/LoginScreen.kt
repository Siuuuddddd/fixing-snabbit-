package com.snabbit.prototype.mobile.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.snabbit.prototype.mobile.R
import com.snabbit.prototype.mobile.navigation.AppRole
import com.snabbit.prototype.mobile.ui.components.BrandLogo
import com.snabbit.prototype.mobile.ui.components.SnabbitButton
import com.snabbit.prototype.mobile.ui.theme.SnabbitSpacing

@Composable
fun LoginScreen(
    state: LoginUiState,
    role: AppRole,
    onPhoneChanged: (String) -> Unit,
    onOtpChanged: (String) -> Unit,
    onRequestOtp: () -> Unit,
    onVerifyOtp: () -> Unit,
    onResendOtp: () -> Unit,
    onEditPhone: () -> Unit,
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
        Spacer(Modifier.height(SnabbitSpacing.medium))
        Icon(
            imageVector = Icons.Outlined.PhoneAndroid,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(SnabbitSpacing.large))
        Text(
            text = stringResource(
                if (state.step == LoginStep.PHONE) R.string.login_title else R.string.otp_title,
            ),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(SnabbitSpacing.small))
        Text(
            text = when (state.step) {
                LoginStep.PHONE -> stringResource(
                    if (role == AppRole.WORKER) R.string.login_worker_description else R.string.login_customer_description,
                )
                LoginStep.OTP -> stringResource(R.string.otp_description, state.phoneDigits)
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(SnabbitSpacing.large))
        when (state.step) {
            LoginStep.PHONE -> PhoneEntry(state, onPhoneChanged, onRequestOtp)
            LoginStep.OTP -> OtpEntry(state, onOtpChanged, onVerifyOtp, onResendOtp, onEditPhone)
        }
        state.error?.let { error ->
            Spacer(Modifier.height(SnabbitSpacing.medium))
            Text(
                text = stringResource(error.messageResource()),
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun PhoneEntry(
    state: LoginUiState,
    onPhoneChanged: (String) -> Unit,
    onRequestOtp: () -> Unit,
) {
    OutlinedTextField(
        value = state.phoneDigits,
        onValueChange = onPhoneChanged,
        modifier = Modifier.fillMaxWidth(),
        enabled = !state.isLoading,
        label = { Text(stringResource(R.string.phone_number_label)) },
        prefix = { Text(stringResource(R.string.india_country_code)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        singleLine = true,
        isError = state.error == LoginError.INVALID_PHONE,
    )
    Spacer(Modifier.height(SnabbitSpacing.standard))
    SnabbitButton(
        text = stringResource(R.string.action_send_otp),
        onClick = onRequestOtp,
        enabled = state.isPhoneValid,
        loading = state.isLoading,
    )
}

@Composable
private fun OtpEntry(
    state: LoginUiState,
    onOtpChanged: (String) -> Unit,
    onVerifyOtp: () -> Unit,
    onResendOtp: () -> Unit,
    onEditPhone: () -> Unit,
) {
    OutlinedTextField(
        value = state.otp,
        onValueChange = onOtpChanged,
        modifier = Modifier.fillMaxWidth(),
        enabled = !state.isLoading,
        label = { Text(stringResource(R.string.otp_label)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        singleLine = true,
        isError = state.error == LoginError.INVALID_OTP,
    )
    Spacer(Modifier.height(SnabbitSpacing.standard))
    SnabbitButton(
        text = stringResource(R.string.action_verify_otp),
        onClick = onVerifyOtp,
        enabled = state.isOtpValid,
        loading = state.isLoading,
    )
    Spacer(Modifier.height(SnabbitSpacing.small))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onEditPhone, enabled = !state.isLoading) {
            Text(stringResource(R.string.action_change_number))
        }
        TextButton(
            onClick = onResendOtp,
            enabled = state.resendSeconds == 0 && !state.isLoading,
        ) {
            Text(
                if (state.resendSeconds > 0) stringResource(R.string.resend_timer, state.resendSeconds)
                else stringResource(R.string.action_resend_otp),
            )
        }
    }
}

private fun LoginError.messageResource(): Int = when (this) {
    LoginError.INVALID_PHONE -> R.string.error_invalid_phone
    LoginError.INVALID_OTP -> R.string.error_invalid_otp
    LoginError.RATE_LIMITED -> R.string.error_rate_limited
    LoginError.OFFLINE -> R.string.error_offline
    LoginError.SERVER -> R.string.error_server
    LoginError.UNKNOWN -> R.string.error_unknown
}
