package com.snabbit.prototype.mobile.ui.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snabbit.prototype.mobile.R
import com.snabbit.prototype.mobile.ui.theme.LocalLoginColors

@Composable
fun CustomerLoginScreen(
    state: CustomerLoginUiState,
    onPhoneChanged: (String) -> Unit,
    onRememberChanged: (Boolean) -> Unit,
    onContinue: () -> Unit,
    onGoogle: () -> Unit,
    onAddGoogleAccount: () -> Unit,
    onEmail: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalLoginColors.current
    val focusManager = LocalFocusManager.current
    val googleDescription = stringResource(R.string.google_login_description)
    Column(modifier.fillMaxSize().background(colors.backdrop)) {
        Image(
            painter = painterResource(R.drawable.login_banner),
            contentDescription = stringResource(R.string.login_banner_description),
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth().aspectRatio(1024f / 500f),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(MaterialTheme.colorScheme.surface)
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.login_or_signup),
                color = colors.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(52.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)),
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_india_flag),
                        contentDescription = stringResource(R.string.india_flag_description),
                        modifier = Modifier.size(width = 30.dp, height = 20.dp),
                    )
                }
                OutlinedTextField(
                    value = state.phoneDigits,
                    onValueChange = onPhoneChanged,
                    singleLine = true,
                    prefix = { Text(stringResource(R.string.india_dial_code)) },
                    placeholder = { Text(stringResource(R.string.enter_phone_number)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        focusManager.clearFocus()
                        if (state.canContinue) onContinue()
                    }),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.accent,
                        cursorColor = colors.accent,
                    ),
                    modifier = Modifier.weight(1f).height(52.dp),
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Checkbox(
                    checked = state.rememberMe,
                    onCheckedChange = onRememberChanged,
                    colors = CheckboxDefaults.colors(checkedColor = colors.accent),
                )
                Text(stringResource(R.string.remember_login), style = MaterialTheme.typography.bodyMedium)
            }
            Button(
                onClick = onContinue,
                enabled = state.canContinue,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accent,
                    disabledContainerColor = colors.accent.copy(alpha = 0.4f),
                    disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                ),
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.login_continue), fontSize = 20.sp, color = MaterialTheme.colorScheme.onPrimary)
                }
            }
            state.error?.let {
                Text(
                    text = stringResource(it.messageRes()),
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 10.dp),
                )
                if (it == CustomerLoginError.NO_GOOGLE_ACCOUNT) {
                    TextButton(onClick = onAddGoogleAccount, enabled = !state.isLoading) {
                        Text(stringResource(R.string.add_google_account), color = colors.accent)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedIconButton(
                    onClick = onGoogle,
                    enabled = !state.isLoading,
                    shape = CircleShape,
                    modifier = Modifier.size(56.dp).semantics {
                        contentDescription = googleDescription
                        role = Role.Button
                    },
                ) {
                    Image(painterResource(R.drawable.ic_google_g), contentDescription = null, modifier = Modifier.size(22.dp))
                }
                OutlinedIconButton(
                    onClick = onEmail,
                    enabled = !state.isLoading,
                    shape = CircleShape,
                    modifier = Modifier.size(56.dp),
                ) {
                    Icon(
                        Icons.Outlined.Email,
                        contentDescription = stringResource(R.string.email_login_description),
                        tint = colors.accent,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(R.string.login_legal_intro),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(top = 4.dp),
            ) {
                LegalLabel(R.string.terms_of_service)
                LegalLabel(R.string.privacy_policy)
                LegalLabel(R.string.content_policy)
            }
        }
    }
}

@Composable
private fun LegalLabel(label: Int) {
    val color = MaterialTheme.colorScheme.onSurface
    Text(
        text = stringResource(label),
        color = color,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.drawBehind {
            drawLine(
                color = color,
                start = androidx.compose.ui.geometry.Offset(0f, size.height),
                end = androidx.compose.ui.geometry.Offset(size.width, size.height),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 2.dp.toPx())),
            )
        },
    )
}

private fun CustomerLoginError.messageRes(): Int = when (this) {
    CustomerLoginError.INVALID_PHONE -> R.string.login_error_invalid_phone
    CustomerLoginError.NOT_CONFIGURED -> R.string.auth_error_not_configured
    CustomerLoginError.NO_GOOGLE_ACCOUNT -> R.string.auth_error_no_google_account
    CustomerLoginError.OFFLINE -> R.string.auth_error_offline
    CustomerLoginError.RATE_LIMITED -> R.string.auth_error_rate_limited
    CustomerLoginError.SERVER -> R.string.login_error_server
    CustomerLoginError.UNKNOWN -> R.string.auth_error_unknown
}
