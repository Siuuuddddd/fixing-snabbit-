package com.snabbit.prototype.mobile.ui.booking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.snabbit.prototype.mobile.R
import com.snabbit.prototype.mobile.domain.booking.BookingType
import com.snabbit.prototype.mobile.ui.common.DataErrorPanel
import com.snabbit.prototype.mobile.ui.common.formatInrMinor
import com.snabbit.prototype.mobile.ui.components.InfoChip
import com.snabbit.prototype.mobile.ui.components.SnabbitButton
import com.snabbit.prototype.mobile.ui.components.SnabbitCard
import com.snabbit.prototype.mobile.ui.theme.SnabbitSpacing
import java.time.LocalDate
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SlotSelectionScreen(
    state: SlotSelectionUiState,
    onBack: () -> Unit,
    onTypeSelected: (BookingType) -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    onTimeSelected: (LocalTime) -> Unit,
    onCheck: () -> Unit,
    onRetry: () -> Unit,
    onContinue: () -> Unit,
) {
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.screen_slot_selection)) }, navigationIcon = {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.action_back)) }
    }) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(SnabbitSpacing.standard),
            verticalArrangement = Arrangement.spacedBy(SnabbitSpacing.standard),
        ) {
            item { Text(stringResource(R.string.slot_booking_type), style = MaterialTheme.typography.titleLarge) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(SnabbitSpacing.small)) {
                    items(BookingType.entries) { type ->
                        InfoChip(stringResource(type.labelRes()), { onTypeSelected(type) }, type == state.bookingType)
                    }
                }
            }
            item { Text(stringResource(R.string.slot_choose_days), style = MaterialTheme.typography.titleLarge) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(SnabbitSpacing.small)) {
                    items(state.dates) { date ->
                        InfoChip(date.format(SlotSelectionViewModel.dayFormatter), { onDateSelected(date) }, date in state.selectedDates)
                    }
                }
            }
            item { Text(stringResource(R.string.slot_choose_time), style = MaterialTheme.typography.titleLarge) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(SnabbitSpacing.small)) {
                    items(state.times) { time ->
                        InfoChip(time.format(SlotSelectionViewModel.timeFormatter), { onTimeSelected(time) }, time == state.selectedTime)
                    }
                }
            }
            state.validationError?.let { error -> item { Text(stringResource(error.messageRes()), color = MaterialTheme.colorScheme.error) } }
            state.error?.let { item { DataErrorPanel(it, onRetry) } }
            item { SnabbitButton(stringResource(R.string.slot_check_action), onCheck, loading = state.isChecking) }
            state.availability?.let { availability ->
                item {
                    SnabbitCard(Modifier.fillMaxWidth()) {
                        Text(
                            stringResource(if (availability.allVisitsAvailable) R.string.slot_available else R.string.slot_unavailable),
                            style = MaterialTheme.typography.titleLarge,
                            color = if (availability.allVisitsAvailable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        )
                        availability.visits.forEach { visit ->
                            Text(
                                if (visit.available) stringResource(R.string.slot_visit_available, visit.compatibleWorkerCount, formatInrMinor(visit.lowestPriceMinor ?: 0))
                                else stringResource(R.string.slot_visit_unavailable),
                            )
                        }
                        if (state.bookingType == BookingType.MULTI_DAY && !availability.sameWorkerAvailableForAllVisits) {
                            Text(stringResource(R.string.slot_different_workers_note), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (availability.allVisitsAvailable) item { SnabbitButton(stringResource(R.string.action_continue), onContinue) }
            }
        }
    }
}

private fun BookingType.labelRes() = when (this) {
    BookingType.INSTANT -> R.string.slot_type_instant
    BookingType.SCHEDULED -> R.string.slot_type_scheduled
    BookingType.MULTI_DAY -> R.string.slot_type_multi_day
}

private fun SlotValidationError.messageRes() = when (this) {
    SlotValidationError.TIME_REQUIRED -> R.string.slot_error_time
    SlotValidationError.ONE_DAY_REQUIRED -> R.string.slot_error_one_day
    SlotValidationError.MULTI_DAY_COUNT -> R.string.slot_error_multi_day
}
