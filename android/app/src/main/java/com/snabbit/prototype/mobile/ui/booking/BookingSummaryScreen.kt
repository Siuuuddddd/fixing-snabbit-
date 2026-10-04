package com.snabbit.prototype.mobile.ui.booking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.snabbit.prototype.mobile.R
import com.snabbit.prototype.mobile.domain.booking.BookingType
import com.snabbit.prototype.mobile.ui.common.DataErrorPanel
import com.snabbit.prototype.mobile.ui.common.formatInrMinor
import com.snabbit.prototype.mobile.ui.components.SnabbitButton
import com.snabbit.prototype.mobile.ui.components.SnabbitCard
import com.snabbit.prototype.mobile.ui.theme.SnabbitSpacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookingSummaryScreen(
    state: BookingSummaryUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onUseWalletChanged: (Boolean) -> Unit,
    onConfirm: () -> Unit,
) {
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.screen_booking_summary)) }, navigationIcon = {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.action_back)) }
    }) }) { padding ->
        if (state.isLoading) {
            Column(Modifier.fillMaxSize().padding(padding), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { CircularProgressIndicator() }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(SnabbitSpacing.standard),
                verticalArrangement = Arrangement.spacedBy(SnabbitSpacing.standard),
            ) {
                state.error?.let { item { DataErrorPanel(it, onRetry) } }
                state.draft?.let { draft ->
                    item {
                        SnabbitCard(Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.summary_schedule_title), style = MaterialTheme.typography.titleLarge)
                            Text(stringResource(R.string.summary_booking_type, stringResource(draft.bookingType.summaryLabelRes())))
                            Text(stringResource(R.string.summary_duration, draft.durationHours))
                            draft.startsAt.forEach { start -> Text(formatVisit(start)) }
                        }
                    }
                }
                item {
                    SnabbitCard(Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.summary_price_title), style = MaterialTheme.typography.titleLarge)
                        SummaryLine(stringResource(R.string.summary_service_total), formatInrMinor(state.estimatedTotalMinor))
                        SummaryLine(stringResource(R.string.summary_wallet), "− ${formatInrMinor(state.estimatedWalletMinor)}")
                        SummaryLine(stringResource(R.string.summary_upi), formatInrMinor(state.estimatedUpiMinor))
                        Text(stringResource(R.string.summary_estimate_note), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                item {
                    SnabbitCard(Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.summary_payment_choice), style = MaterialTheme.typography.titleLarge)
                        PaymentChoice(stringResource(R.string.summary_wallet_and_upi), state.useWallet, { onUseWalletChanged(true) }, state.wallet != null)
                        PaymentChoice(stringResource(R.string.summary_upi_only), !state.useWallet, { onUseWalletChanged(false) }, true)
                        if (state.wallet == null) Text(stringResource(R.string.summary_wallet_unavailable), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                item {
                    Text(stringResource(R.string.summary_worker_first_note), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                item {
                    SnabbitButton(
                        stringResource(R.string.summary_confirm_action),
                        onConfirm,
                        enabled = state.availability?.allVisitsAvailable == true,
                        loading = state.isConfirming,
                    )
                }
            }
        }
    }
}

private fun formatVisit(value: String): String = runCatching {
    Instant.parse(value).atZone(ZoneId.of("Asia/Kolkata")).format(DateTimeFormatter.ofPattern("EEE, d MMM · h:mm a"))
}.getOrDefault(value)

private fun BookingType.summaryLabelRes() = when (this) {
    BookingType.INSTANT -> R.string.slot_type_instant
    BookingType.SCHEDULED -> R.string.slot_type_scheduled
    BookingType.MULTI_DAY -> R.string.slot_type_multi_day
}

@Composable private fun SummaryLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label); Text(value) }
}

@Composable private fun PaymentChoice(label: String, selected: Boolean, onClick: () -> Unit, enabled: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick, enabled = enabled)
        Text(label, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
