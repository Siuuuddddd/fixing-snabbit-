package com.snabbit.prototype.mobile.ui.booking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
    onSelectWorker: (String) -> Unit,
    onHomeNameChanged: (String) -> Unit,
    onHomePhoneChanged: (String) -> Unit,
    onPay: () -> Unit,
    onOtpChanged: (String) -> Unit,
    onVerifyOtp: () -> Unit,
    onResendOtp: () -> Unit,
) {
    Scaffold { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(SnabbitSpacing.large),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(SnabbitSpacing.standard, Alignment.CenterVertically),
        ) {
            if (state.isLoading) CircularProgressIndicator()
            if (state.booking?.status == BookingStatus.AWAITING_WORKER) {
                Text("Choose a worker for visit ${state.activeVisitIndex + 1}", style = MaterialTheme.typography.headlineSmall)
                if (state.favourites.isNotEmpty()) Text("Your favourites", style = MaterialTheme.typography.titleLarge)
                state.favourites.forEach { worker ->
                    SnabbitCard(Modifier.fillMaxWidth()) {
                        Text("${worker.firstName} • ${worker.tier}", style = MaterialTheme.typography.titleMedium)
                        Text("★ ${worker.rating} (${worker.ratingCount}) • ${worker.distanceKm} km")
                        Text("${formatInrMinor(worker.priceMinor)} • surcharge ${formatInrMinor(worker.tierSurchargeMinor)}")
                        SnabbitButton("Select ${worker.firstName}", { onSelectWorker(worker.workerId) }, enabled = !state.isSubmitting)
                    }
                }
                if (state.others.isNotEmpty()) Text("Other available workers", style = MaterialTheme.typography.titleLarge)
                state.others.forEach { worker ->
                    SnabbitCard(Modifier.fillMaxWidth()) {
                        Text("${worker.firstName} • ${worker.tier}", style = MaterialTheme.typography.titleMedium)
                        Text("★ ${worker.rating} (${worker.ratingCount}) • ${worker.distanceKm} km")
                        Text(formatInrMinor(worker.priceMinor))
                        SnabbitButton("Select ${worker.firstName}", { onSelectWorker(worker.workerId) }, enabled = !state.isSubmitting)
                    }
                }
                if (!state.isLoading && state.favourites.isEmpty() && state.others.isEmpty()) {
                    Text("No worker available, change the time", color = MaterialTheme.colorScheme.error)
                }
            }
            if (state.booking?.status == BookingStatus.AWAITING_PAYMENT) {
                Text("Who will be at home?", style = MaterialTheme.typography.headlineSmall)
                Text("Leave both fields blank to use your verified profile details.")
                OutlinedTextField(state.homeContactName, onHomeNameChanged, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(state.homeContactPhone, onHomePhoneChanged, label = { Text("Indian mobile number") }, prefix = { Text("+91 ") }, modifier = Modifier.fillMaxWidth())
                state.booking.visits.forEach { visit ->
                    Text("Visit ${visit.visitIndex + 1}: ${visit.selectedWorkerName ?: "Selected worker"} • ${visit.durationMinutes / 60} hr")
                }
                SnabbitButton("Pay ${formatInrMinor(state.booking.quotedTotalMinor)}", onPay, enabled = !state.isSubmitting)
            }
            state.booking?.takeIf { it.status == BookingStatus.CONFIRMED }?.let { booking ->
                val arrived = booking.visits.firstOrNull { it.jobStatus == "ARRIVED" }
                if (arrived == null) {
                    Text("Expert will arrive soon", style = MaterialTheme.typography.headlineSmall)
                    Text("%02d:%02d".format(state.remainingSeconds / 60, state.remainingSeconds % 60), style = MaterialTheme.typography.displaySmall)
                    booking.visits.firstOrNull { it.jobStatus != "COMPLETED" }?.let { Text(it.selectedWorkerName ?: "Your worker") }
                } else {
                    Text("Hurray! Your worker has arrived", style = MaterialTheme.typography.headlineSmall)
                    Text("${arrived.selectedWorkerName ?: "Your worker"} • ${arrived.selectedWorkerTier.orEmpty()}")
                    booking.maskedHomeContactPhone?.let { Text("Code sent to $it") }
                    OutlinedTextField(state.otp, onOtpChanged, label = { Text("6-digit OTP") }, modifier = Modifier.fillMaxWidth())
                    SnabbitButton("Start service", onVerifyOtp, enabled = state.otp.length == 6 && !state.isSubmitting)
                    OutlinedButton(onClick = onResendOtp, modifier = Modifier.fillMaxWidth()) { Text("Resend OTP") }
                    state.otpMessage?.let { Text(it) }
                }
            }
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
                visit.workerId?.let { workerId ->
                    SnabbitButton(stringResource(R.string.completed_rate_prompt), { onCompleted(visit.visitIndex, workerId) })
                }
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
