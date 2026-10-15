package com.snabbit.prototype.mobile.ui.emailauth

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.autofill.contentType
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.snabbit.prototype.mobile.R
import com.snabbit.prototype.mobile.domain.auth.PasswordRule
import com.snabbit.prototype.mobile.domain.auth.PasswordValidation
import com.snabbit.prototype.mobile.ui.theme.SnabbitSpacing

@Composable
internal fun EmailField(
    value: String,
    onValueChange: (String) -> Unit,
    isError: Boolean,
    onBlur: () -> Unit,
    imeAction: ImeAction,
    onNext: () -> Unit = {},
    onDone: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().contentType(ContentType.EmailAddress).onBlur(onBlur),
        label = { Text(stringResource(R.string.email_auth_email)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = imeAction),
        keyboardActions = KeyboardActions(onNext = { onNext() }, onDone = { onDone() }),
        singleLine = true,
        isError = isError,
        supportingText = if (isError) ({ Text(stringResource(R.string.email_auth_invalid_email)) }) else null,
    )
}

@Composable
internal fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    visible: Boolean,
    onToggleVisibility: () -> Unit,
    isError: Boolean,
    onBlur: () -> Unit,
    @StringRes label: Int,
    imeAction: ImeAction,
    onDone: () -> Unit,
    onNext: () -> Unit = {},
    modifier: Modifier = Modifier,
    newPassword: Boolean = false,
) {
    val visibilityDescription = stringResource(if (visible) R.string.email_auth_hide_password else R.string.email_auth_show_password)
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().contentType(if (newPassword) ContentType.NewPassword else ContentType.Password).onBlur(onBlur),
        label = { Text(stringResource(label)) },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = imeAction,
            autoCorrectEnabled = false,
        ),
        keyboardActions = KeyboardActions(onNext = { onNext() }, onDone = { onDone() }),
        singleLine = true,
        isError = isError,
        supportingText = if (isError) ({ Text(stringResource(R.string.email_auth_invalid_password)) }) else null,
        trailingIcon = {
            IconButton(onClick = onToggleVisibility, modifier = Modifier.size(48.dp)) {
                Icon(if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, visibilityDescription)
            }
        },
    )
}

@Composable
internal fun PasswordRulesChecklist(validation: PasswordValidation, includeIdentityRules: Boolean = true) {
    Column(verticalArrangement = Arrangement.spacedBy(SnabbitSpacing.extraSmall)) {
        PasswordRuleRow(PasswordRule.LENGTH, R.string.email_auth_rule_length, validation)
        PasswordRuleRow(PasswordRule.NOT_COMMON, R.string.email_auth_rule_common, validation)
        if (includeIdentityRules) {
            PasswordRuleRow(PasswordRule.DIFFERS_FROM_EMAIL, R.string.email_auth_rule_email, validation)
            PasswordRuleRow(PasswordRule.DIFFERS_FROM_NAME, R.string.email_auth_rule_name, validation)
        }
    }
}

@Composable
private fun PasswordRuleRow(rule: PasswordRule, @StringRes text: Int, validation: PasswordValidation) {
    val passed = validation.passes(rule)
    val status = stringResource(if (passed) R.string.email_auth_rule_passed else R.string.email_auth_rule_failed)
    val label = stringResource(text)
    Row(
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "$status: $label" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SnabbitSpacing.small),
    ) {
        Icon(
            if (passed) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (passed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
internal fun EmailAuthErrorText(error: EmailAuthError?) {
    if (error == null) return
    Text(
        text = when {
            error.type == EmailAuthUiError.RATE_LIMITED && error.retryAfterSeconds != null ->
                stringResource(R.string.email_auth_error_rate_limited_seconds, error.retryAfterSeconds)
            else -> stringResource(error.type.messageRes())
        },
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium,
    )
}

@StringRes
private fun EmailAuthUiError.messageRes(): Int = when (this) {
    EmailAuthUiError.INVALID_CREDENTIALS -> R.string.email_auth_error_invalid_credentials
    EmailAuthUiError.EMAIL_NOT_VERIFIED -> R.string.email_auth_error_unverified
    EmailAuthUiError.WEAK_PASSWORD -> R.string.email_auth_error_weak_password
    EmailAuthUiError.INVALID_OR_EXPIRED_CODE -> R.string.email_auth_error_invalid_code
    EmailAuthUiError.TOO_MANY_ATTEMPTS -> R.string.email_auth_error_too_many_attempts
    EmailAuthUiError.RATE_LIMITED -> R.string.email_auth_error_rate_limited
    EmailAuthUiError.TERMS_NOT_ACCEPTED -> R.string.email_auth_error_terms
    EmailAuthUiError.ACCOUNT_DISABLED -> R.string.email_auth_error_disabled
    EmailAuthUiError.PASSWORD_NOT_SET -> R.string.email_auth_error_password_not_set
    EmailAuthUiError.VALIDATION_ERROR -> R.string.email_auth_error_validation
    EmailAuthUiError.SESSION_REVOKED -> R.string.email_auth_error_session_revoked
    EmailAuthUiError.OFFLINE -> R.string.email_auth_error_offline
    EmailAuthUiError.UNEXPECTED -> R.string.email_auth_error_unexpected
}

private fun Modifier.onBlur(action: () -> Unit): Modifier {
    var focused = false
    return onFocusChanged { state ->
        if (focused && !state.isFocused) action()
        focused = state.isFocused
    }
}

