package com.snabbit.prototype.mobile.ui.cancellation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.snabbit.prototype.mobile.R
import com.snabbit.prototype.mobile.domain.cancellation.CancellationPolicyBand
import com.snabbit.prototype.mobile.ui.common.DataErrorPanel
import com.snabbit.prototype.mobile.ui.common.formatInrMinor
import com.snabbit.prototype.mobile.ui.components.SnabbitButton
import com.snabbit.prototype.mobile.ui.components.SnabbitCard
import com.snabbit.prototype.mobile.ui.theme.ConfirmedGreen
import com.snabbit.prototype.mobile.ui.theme.SnabbitSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CancelBookingScreen(
    state: CancelBookingUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onReasonChanged: (String) -> Unit,
    onConfirm: () -> Unit,
) {
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.screen_cancel_booking)) }, navigationIcon = {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.action_back)) }
    }) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(SnabbitSpacing.standard),
            verticalArrangement = Arrangement.spacedBy(SnabbitSpacing.standard),
        ) {
            if (state.isLoading) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
            state.error?.let { DataErrorPanel(it, onRetry) }
            state.quote?.let { quote ->
                val finalFee = state.cancelledBooking?.cancellationFeeMinor ?: quote.cancellationFeeMinor
                val finalRefund = state.cancelledBooking?.refundAmountMinor ?: quote.refundAmountMinor
                SnabbitCard(Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.cancel_refund_title), style = MaterialTheme.typography.titleLarge)
                    MoneyLine(stringResource(R.string.cancel_charged), formatInrMinor(finalFee + finalRefund))
                    MoneyLine(stringResource(R.string.cancel_fee), "− ${formatInrMinor(finalFee)}")
                    MoneyLine(stringResource(R.string.cancel_refund), formatInrMinor(finalRefund))
                    Text(stringResource(quote.policyBand.labelRes()), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (quote.plusActive) Text(stringResource(R.string.cancel_plus_applied), color = ConfirmedGreen)
                }
                if (state.cancelledBooking == null) {
                    OutlinedTextField(
                        state.reason,
                        onReasonChanged,
                        Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.cancel_reason_label)) },
                        supportingText = { Text(stringResource(R.string.cancel_reason_count, state.reason.length)) },
                        minLines = 3,
                    )
                    Text(stringResource(R.string.cancel_refund_pending_note), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SnabbitButton(stringResource(R.string.cancel_confirm_action), onConfirm, loading = state.isCancelling)
                } else {
                    Text(stringResource(R.string.cancel_success_title), style = MaterialTheme.typography.titleLarge, color = ConfirmedGreen)
                    Text(stringResource(R.string.cancel_success_refund, formatInrMinor(finalRefund)))
                    Text(stringResource(R.string.cancel_success_pending), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable private fun MoneyLine(label: String, amount: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label); Text(amount) }
}

private fun CancellationPolicyBand.labelRes() = when (this) {
    CancellationPolicyBand.EARLY -> R.string.cancel_policy_early
    CancellationPolicyBand.LATE -> R.string.cancel_policy_late
    CancellationPolicyBand.UNKNOWN -> R.string.cancel_policy_unknown
}
