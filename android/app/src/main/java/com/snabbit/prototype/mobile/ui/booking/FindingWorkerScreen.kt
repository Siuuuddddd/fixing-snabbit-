package com.snabbit.prototype.mobile.ui.booking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.snabbit.prototype.mobile.R
import com.snabbit.prototype.mobile.domain.booking.BookingStatus
import com.snabbit.prototype.mobile.ui.common.DataErrorPanel
import com.snabbit.prototype.mobile.ui.common.formatInrMinor
import com.snabbit.prototype.mobile.ui.components.SnabbitCard
import com.snabbit.prototype.mobile.ui.components.SnabbitButton
import com.snabbit.prototype.mobile.ui.theme.SnabbitSpacing

@Composable
fun FindingWorkerScreen(
    state: FindingWorkerUiState,
    onRetry: () -> Unit,
    onCancelHold: () -> Unit,
    onTrack: (Int) -> Unit,
    onCancelBooking: () -> Unit,
    onCompleted: (Int, String) -> Unit,
) {
    Scaffold { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(SnabbitSpacing.large),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(SnabbitSpacing.standard, Alignment.CenterVertically),
        ) {
            if (state.isLoading) CircularProgressIndicator()
            state.booking?.let { booking ->
                Text(stringResource(booking.status.titleRes()), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(booking.status.messageRes()), color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (booking.status == BookingStatus.AWAITING_WORKER || booking.status == BookingStatus.AWAITING_PAYMENT) CircularProgressIndicator()
                Text(stringResource(R.string.finding_booking_id, booking.id), style = MaterialTheme.typography.bodySmall)
            }
            state.payment?.let { payment ->
                SnabbitCard(Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.finding_payment_title), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.finding_payment_wallet, formatInrMinor(payment.walletAmountMinor)))
                    Text(stringResource(R.string.finding_payment_upi, formatInrMinor(payment.onlineAmountMinor)))
                    Text(stringResource(R.string.finding_payment_status, payment.status))
                    if (payment.onlineAmountMinor > 0) Text(stringResource(R.string.finding_upi_pending), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (state.paymentUnavailable) Text(stringResource(R.string.finding_wallet_id_missing), color = MaterialTheme.colorScheme.error)
            state.error?.let { DataErrorPanel(it, onRetry) }
            if (state.booking?.status == BookingStatus.AWAITING_WORKER) {
                OutlinedButton(onClick = onCancelHold, enabled = !state.isCancelling, modifier = Modifier.fillMaxWidth()) {
                    if (state.isCancelling) CircularProgressIndicator() else Text(stringResource(R.string.finding_cancel_hold))
                }
            }
            state.booking?.takeIf { it.status == BookingStatus.CONFIRMED }?.visits?.firstOrNull()?.let { visit ->
                SnabbitButton(stringResource(R.string.finding_track_worker), { onTrack(visit.visitIndex) })
                OutlinedButton(onClick = onCancelBooking, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.finding_cancel_booking))
                }
            }
            state.booking?.takeIf { it.status == BookingStatus.COMPLETED }?.visits?.lastOrNull()?.let { visit ->
                SnabbitButton(stringResource(R.string.completed_rate_prompt), { onCompleted(visit.visitIndex, visit.workerId) })
            }
        }
    }
}

private fun BookingStatus.titleRes() = when (this) {
    BookingStatus.AWAITING_WORKER -> R.string.finding_worker_title
    BookingStatus.AWAITING_PAYMENT -> R.string.finding_payment_wait_title
    BookingStatus.CONFIRMED -> R.string.finding_confirmed_title
    BookingStatus.WORKER_UNAVAILABLE -> R.string.finding_unavailable_title
    BookingStatus.EXPIRED -> R.string.finding_expired_title
    BookingStatus.CANCELLED -> R.string.finding_cancelled_title
    BookingStatus.COMPLETED -> R.string.finding_completed_title
    BookingStatus.UNKNOWN -> R.string.error_unknown
}

private fun BookingStatus.messageRes() = when (this) {
    BookingStatus.AWAITING_WORKER -> R.string.finding_worker_message
    BookingStatus.AWAITING_PAYMENT -> R.string.finding_payment_wait_message
    BookingStatus.CONFIRMED -> R.string.finding_confirmed_message
    BookingStatus.WORKER_UNAVAILABLE -> R.string.finding_unavailable_message
    BookingStatus.EXPIRED -> R.string.finding_expired_message
    BookingStatus.CANCELLED -> R.string.finding_cancelled_message
    BookingStatus.COMPLETED -> R.string.finding_completed_message
    BookingStatus.UNKNOWN -> R.string.error_unknown
}
