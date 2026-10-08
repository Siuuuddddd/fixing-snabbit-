package com.snabbit.prototype.mobile.ui.booking

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.snabbit.prototype.mobile.R
import com.snabbit.prototype.mobile.domain.home.HabitPackPromo
import com.snabbit.prototype.mobile.domain.schedule.DayPart
import com.snabbit.prototype.mobile.domain.schedule.SchedulePrice
import com.snabbit.prototype.mobile.domain.schedule.ServiceDuration
import com.snabbit.prototype.mobile.domain.schedule.TimeSlot
import com.snabbit.prototype.mobile.ui.common.DataErrorPanel
import com.snabbit.prototype.mobile.ui.common.EmptyStatePanel
import com.snabbit.prototype.mobile.ui.common.formatInrMinor
import com.snabbit.prototype.mobile.ui.theme.ScheduleDisabledPink
import com.snabbit.prototype.mobile.ui.theme.SnabbitSpacing
import com.snabbit.prototype.mobile.ui.theme.SnabbitTextStyles
import com.snabbit.prototype.mobile.ui.theme.SnabbitTheme
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleStartTimeScreen(
    state: ScheduleStartTimeUiState,
    onBack: () -> Unit,
    onEvent: (ScheduleStartTimeEvent) -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbar = remember { SnackbarHostState() }
    val promoMessage = stringResource(R.string.schedule_promo_coming_soon)
    LaunchedEffect(state.promoMessagePending) {
        if (state.promoMessagePending) {
            snackbar.showSnackbar(promoMessage)
            onEvent(ScheduleStartTimeEvent.PromoMessageShown)
        }
    }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.schedule_title), style = SnabbitTextStyles.ScreenTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        bottomBar = {
            Column {
                state.promo?.let { promo ->
                    HabitPackStrip(promo) { onEvent(ScheduleStartTimeEvent.PromoAddSelected) }
                }
                StartTimeBottomBar(state, onContinue)
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(SnabbitSpacing.standard),
            verticalArrangement = Arrangement.spacedBy(SnabbitSpacing.standard),
        ) {
            item { SelectionSummary(state) }
            item { StartTimeCard(state, onEvent) }
            state.error?.let { error -> item { DataErrorPanel(error, onRetry = { onEvent(ScheduleStartTimeEvent.Retry) }) } }
        }
    }
}

@Composable
private fun SelectionSummary(state: ScheduleStartTimeUiState) {
    ScheduleCard {
        Text(stringResource(R.string.schedule_your_selection), style = SnabbitTextStyles.SectionHeading)
        Text(
            state.dates.joinToString { it.format(summaryDateFormatter) },
            style = MaterialTheme.typography.bodyLarge,
        )
        state.duration?.let {
            Text(
                pluralStringResource(R.plurals.schedule_duration_hours, it.hours, it.hours),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StartTimeCard(state: ScheduleStartTimeUiState, onEvent: (ScheduleStartTimeEvent) -> Unit) {
    ScheduleCard(contentPadding = PaddingValues(vertical = SnabbitSpacing.standard)) {
        Text(
            stringResource(R.string.schedule_select_start_time),
            style = SnabbitTextStyles.SectionHeading,
            modifier = Modifier.padding(horizontal = SnabbitSpacing.standard),
        )
        Row(Modifier.fillMaxWidth()) {
            DayPart.entries.forEach { part ->
                val selected = state.dayPart == part
                Column(Modifier.weight(1f).height(56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        onClick = { onEvent(ScheduleStartTimeEvent.DayPartSelected(part)) },
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                stringResource(part.labelRes()),
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                    Box(
                        Modifier.fillMaxWidth().height(if (selected) 4.dp else 1.dp)
                            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                    )
                }
            }
        }
        when {
            state.isLoading -> Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.slots.isEmpty() -> EmptyStatePanel(
                stringResource(R.string.schedule_no_slots_title),
                stringResource(R.string.schedule_no_slots_body),
                Modifier.padding(horizontal = SnabbitSpacing.standard),
            )
            else -> TimeSlotGrid(state.slots, state.selectedTime) { onEvent(ScheduleStartTimeEvent.TimeSelected(it)) }
        }
    }
}

@Composable
private fun TimeSlotGrid(slots: List<TimeSlot>, selected: LocalTime?, onSelected: (LocalTime) -> Unit) {
    val columns = if (LocalDensity.current.fontScale >= 1.5f) 2 else 4
    Column(
        Modifier.padding(horizontal = SnabbitSpacing.standard),
        verticalArrangement = Arrangement.spacedBy(SnabbitSpacing.medium),
    ) {
        slots.chunked(columns).forEach { rowSlots ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(SnabbitSpacing.medium)) {
                rowSlots.forEach { slot ->
                    SelectionTile(
                        selected = slot.available && selected == slot.time,
                        enabled = slot.available,
                        onClick = { onSelected(slot.time) },
                        modifier = Modifier.weight(1f).height(82.dp),
                    ) {
                        Column(
                            Modifier.fillMaxSize().padding(SnabbitSpacing.extraSmall),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                slot.time.format(timeFormatter),
                                color = if (slot.available) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                            )
                            if (!slot.available) Text(
                                stringResource(R.string.schedule_sold_out),
                                color = MaterialTheme.colorScheme.outline,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
                repeat(columns - rowSlots.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun StartTimeBottomBar(state: ScheduleStartTimeUiState, onContinue: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(SnabbitSpacing.standard),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SnabbitSpacing.standard),
    ) {
        Text(
            state.totalPriceMinor?.let(::formatInrMinor).orEmpty(),
            modifier = Modifier.weight(0.7f),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Button(
            onClick = onContinue,
            enabled = state.canContinue,
            modifier = Modifier.weight(1.3f).height(56.dp),
            colors = ButtonDefaults.buttonColors(
                disabledContainerColor = ScheduleDisabledPink,
                disabledContentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) { Text(stringResource(R.string.action_continue), fontWeight = FontWeight.Bold) }
    }
}

private fun DayPart.labelRes() = when (this) {
    DayPart.MORNING -> R.string.schedule_morning
    DayPart.AFTERNOON -> R.string.schedule_afternoon
    DayPart.EVENING -> R.string.schedule_evening
}

private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")
private val summaryDateFormatter = DateTimeFormatter.ofPattern("EEE, d MMM")

@Preview(name = "Schedule start time", widthDp = 411, heightDp = 914, showBackground = true)
@Preview(name = "Schedule start time dark", widthDp = 411, heightDp = 914, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ScheduleStartTimePreview() = SnabbitTheme {
    ScheduleStartTimeScreen(
        ScheduleStartTimeUiState(
            dates = listOf(LocalDate.of(2026, 10, 11)),
            duration = ServiceDuration(2, SchedulePrice(27_000)),
            slots = (0..11).map { TimeSlot(LocalTime.of(8 + it / 4, it % 4 * 15), it != 0, 27_000) },
            selectedTime = LocalTime.of(8, 15),
            promo = HabitPackPromo(3, 89_700, 27_900),
            isLoading = false,
        ), {}, {}, {},
    )
}
