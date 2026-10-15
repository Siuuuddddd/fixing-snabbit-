package com.snabbit.prototype.mobile.ui.profile

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.snabbit.prototype.mobile.R

@Composable
fun PhoneRequiredDialog(
    phoneDigits: String,
    isSaving: Boolean,
    isInvalid: Boolean,
    onPhoneChanged: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.phone_required_title)) },
        text = {
            OutlinedTextField(
                value = phoneDigits,
                onValueChange = onPhoneChanged,
                label = { Text(stringResource(R.string.profile_phone_label)) },
                prefix = { Text("+91 ") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                isError = isInvalid,
                supportingText = if (isInvalid) {{ Text(stringResource(R.string.profile_phone_invalid)) }} else null,
            )
        },
        confirmButton = {
            TextButton(onClick = onSave, enabled = !isSaving) {
                Text(stringResource(if (isSaving) R.string.profile_phone_saving else R.string.profile_phone_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
