package com.snabbit.prototype.mobile.ui.booking

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import com.snabbit.prototype.mobile.domain.schedule.MultiDayValidationError
import com.snabbit.prototype.mobile.domain.schedule.ScheduleDateOption
import com.snabbit.prototype.mobile.domain.schedule.ScheduleDateOptions
import com.snabbit.prototype.mobile.domain.schedule.SchedulePrice
import com.snabbit.prototype.mobile.domain.schedule.ServiceDuration
import com.snabbit.prototype.mobile.domain.schedule.TimeSlot
import com.snabbit.prototype.mobile.ui.common.DataErrorPanel
import com.snabbit.prototype.mobile.ui.common.EmptyStatePanel
import com.snabbit.prototype.mobile.ui.common.formatInrMinor
import com.snabbit.prototype.mobile.ui.theme.HomePromoEnd
import com.snabbit.prototype.mobile.ui.theme.HomePromoStart
import com.snabbit.prototype.mobile.ui.theme.ScheduleDisabledPink
import com.snabbit.prototype.mobile.ui.theme.SnabbitSpacing
import com.snabbit.prototype.mobile.ui.theme.SnabbitTheme
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    state: ScheduleUiState,
    onBack: () -> Unit,
    onEvent: (ScheduleUiEvent) -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val promoMessage = stringResource(R.string.schedule_promo_coming_soon)
    LaunchedEffect(state.promoMessagePending) {
        if (state.promoMessagePending) {
            snackbarHostState.showSnackbar(promoMessage)
            onEvent(ScheduleUiEvent.PromoMessageShown)
        }
    }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.schedule_title),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        bottomBar = {
            ScheduleBottomBar(
                state = state,
                onPromoAdd = { onEvent(ScheduleUiEvent.PromoAddSelected) },
                onContinue = onContinue,
            )
        },
    ) { padding ->
        when {
            state.isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.error != null && state.durations.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                DataErrorPanel(state.error, onRetry = { onEvent(ScheduleUiEvent.Retry) })
            }
            else -> ScheduleContent(state, onEvent, Modifier.padding(padding))
        }
    }
}

@Composable
private fun ScheduleContent(
    state: ScheduleUiState,
    onEvent: (ScheduleUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(SnabbitSpacing.standard),
        verticalArrangement = Arrangement.spacedBy(SnabbitSpacing.standard),
    ) {
        item { DateSelectionCard(state, onEvent) }
        if (state.hasValidDates) {
            item { DurationSelectionCard(state, onEvent) }
        }
        if (state.selectedDuration != null) {
            item { StartTimeCard(state, onEvent) }
        }
        state.error?.let { error ->
            item { DataErrorPanel(error, onRetry = { onEvent(ScheduleUiEvent.Retry) }) }
        }
    }
}

@Composable
private fun DateSelectionCard(state: ScheduleUiState, onEvent: (ScheduleUiEvent) -> Unit) {
    ScheduleCard {
        Text(stringResource(R.string.schedule_select_date), style = MaterialTheme.typography.titleLarge)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SnabbitSpacing.medium),
        ) {
            ScheduleDateOption.entries.forEach { option ->
                SelectionTile(
                    selected = state.selectedDateOption == option,
                    onClick = { onEvent(ScheduleUiEvent.DateOptionSelected(option)) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        stringResource(option.labelRes()),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        if (state.selectedDateOption == ScheduleDateOption.MULTI_DAY) {
            MultiDayDropdown(state, onEvent)
            state.multiDayError?.let { error ->
                Text(
                    stringResource(error.messageRes()),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (state.selectedDates.isNotEmpty()) {
                Text(
                    stringResource(
                        R.string.schedule_selected_days_summary,
                        state.selectedDates.sorted().joinToString { it.format(shortDateFormatter) },
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun MultiDayDropdown(state: ScheduleUiState, onEvent: (ScheduleUiEvent) -> Unit) {
    Column {
        Surface(
            onClick = { onEvent(ScheduleUiEvent.MultiDayMenuToggled) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = SnabbitSpacing.standard),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.schedule_choose_multiple_days),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Icon(
                    if (state.isMultiDayMenuExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = stringResource(R.string.schedule_toggle_day_menu),
                )
            }
        }
        AnimatedVisibility(state.isMultiDayMenuExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = SnabbitSpacing.small)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                state.dateOptions?.multiDayChoices.orEmpty().forEach { date ->
                    val selected = date in state.selectedDates
                    Surface(
                        onClick = { onEvent(ScheduleUiEvent.MultiDayToggled(date)) },
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(SnabbitSpacing.medium),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(date.format(fullDateFormatter), modifier = Modifier.weight(1f))
                            Surface(
                                modifier = Modifier.size(24.dp),
                                shape = RoundedCornerShape(6.dp),
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                            ) {
                                if (selected) Icon(Icons.Outlined.Check, null, tint = MaterialTheme.colorScheme.onPrimary)
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun DurationSelectionCard(state: ScheduleUiState, onEvent: (ScheduleUiEvent) -> Unit) {
    ScheduleCard(contentPadding = PaddingValues(vertical = SnabbitSpacing.standard)) {
        Text(
            stringResource(R.string.schedule_select_duration),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = SnabbitSpacing.standard),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = SnabbitSpacing.standard),
            horizontalArrangement = Arrangement.spacedBy(SnabbitSpacing.medium),
        ) {
            items(state.durations, key = { it.hours }) { duration ->
                DurationCard(
                    duration = duration,
                    selected = state.selectedDuration?.hours == duration.hours,
                    onClick = { onEvent(ScheduleUiEvent.DurationSelected(duration)) },
                )
            }
        }
        Text(
            stringResource(R.string.schedule_whole_hour_note),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = SnabbitSpacing.standard),
        )
    }
}

@Composable
private fun DurationCard(duration: ServiceDuration, selected: Boolean, onClick: () -> Unit) {
    SelectionTile(
        selected = selected,
        onClick = onClick,
        modifier = Modifier.width(140.dp).height(112.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(SnabbitSpacing.standard),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                pluralStringResource(R.plurals.schedule_duration_hours, duration.hours, duration.hours),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(formatInrMinor(duration.price.currentPriceMinor), style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun StartTimeCard(state: ScheduleUiState, onEvent: (ScheduleUiEvent) -> Unit) {
    ScheduleCard(contentPadding = PaddingValues(vertical = SnabbitSpacing.standard)) {
        Text(
            stringResource(R.string.schedule_select_start_time),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = SnabbitSpacing.standard),
        )
        Row(Modifier.fillMaxWidth()) {
            DayPart.entries.forEach { dayPart ->
                val selected = state.dayPart == dayPart
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    Surface(
                        onClick = { onEvent(ScheduleUiEvent.DayPartSelected(dayPart)) },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        color = MaterialTheme.colorScheme.surface,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                stringResource(dayPart.labelRes()),
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(if (selected) 4.dp else 1.dp)
                            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                    )
                }
            }
        }
        when {
            state.isLoadingSlots -> Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.slots.isEmpty() -> EmptyStatePanel(
                title = stringResource(R.string.schedule_no_slots_title),
                description = stringResource(R.string.schedule_no_slots_body),
                modifier = Modifier.padding(horizontal = SnabbitSpacing.standard),
            )
            else -> TimeSlotGrid(state.slots, state.selectedTime) { onEvent(ScheduleUiEvent.TimeSelected(it)) }
        }
    }
}

@Composable
private fun TimeSlotGrid(slots: List<TimeSlot>, selectedTime: LocalTime?, onSelected: (LocalTime) -> Unit) {
    val columns = if (LocalDensity.current.fontScale >= 1.5f) 2 else 4
    Column(
        modifier = Modifier.padding(horizontal = SnabbitSpacing.standard),
        verticalArrangement = Arrangement.spacedBy(SnabbitSpacing.medium),
    ) {
        slots.chunked(columns).forEach { rowSlots ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(SnabbitSpacing.medium)) {
                rowSlots.forEach { slot ->
                    SelectionTile(
                        selected = slot.available && selectedTime == slot.time,
                        enabled = slot.available,
                        onClick = { onSelected(slot.time) },
                        modifier = Modifier.weight(1f).height(82.dp),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(SnabbitSpacing.extraSmall),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                slot.time.format(timeFormatter),
                                color = if (slot.available) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                            )
                            if (!slot.available) {
                                Text(
                                    stringResource(R.string.schedule_sold_out),
                                    color = MaterialTheme.colorScheme.outline,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
                repeat(columns - rowSlots.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun SelectionTile(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(64.dp),
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
        content = content,
    )
}

@Composable
private fun ScheduleCard(
    contentPadding: PaddingValues = PaddingValues(SnabbitSpacing.standard),
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(SnabbitSpacing.medium),
            content = { content() },
        )
    }
}

@Composable
private fun ScheduleBottomBar(state: ScheduleUiState, onPromoAdd: () -> Unit, onContinue: () -> Unit) {
    Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
        if (state.hasValidDates) {
            state.promo?.let { HabitPackStrip(it, onPromoAdd) }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(SnabbitSpacing.standard),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SnabbitSpacing.standard),
        ) {
            state.selectedDuration?.let {
                Text(
                    state.totalPriceMinor?.let(::formatInrMinor).orEmpty(),
                    modifier = Modifier.weight(0.7f),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Button(
                onClick = onContinue,
                enabled = state.canContinue,
                modifier = Modifier.weight(if (state.selectedDuration == null) 1f else 1.3f).height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = ScheduleDisabledPink,
                    disabledContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text(stringResource(R.string.action_continue), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun HabitPackStrip(promo: HabitPackPromo, onAdd: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.horizontalGradient(listOf(HomePromoStart, HomePromoEnd)))
            .padding(horizontal = SnabbitSpacing.standard, vertical = SnabbitSpacing.medium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SnabbitSpacing.medium),
    ) {
        Icon(
            Icons.Outlined.ConfirmationNumber,
            contentDescription = stringResource(R.string.home_ticket_art_placeholder),
            modifier = Modifier.size(48.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.schedule_promo_title, formatInrMinor(promo.offerPriceMinor)),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                stringResource(
                    R.string.schedule_promo_subtitle,
                    promo.visitCount,
                    formatInrMinor(promo.offerPriceMinor / promo.visitCount),
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Button(
            onClick = onAdd,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.onSurface,
                contentColor = MaterialTheme.colorScheme.surface,
            ),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(stringResource(R.string.schedule_promo_add), fontWeight = FontWeight.Bold)
        }
    }
}

private fun ScheduleDateOption.labelRes() = when (this) {
    ScheduleDateOption.TODAY -> R.string.schedule_today
    ScheduleDateOption.TOMORROW -> R.string.schedule_tomorrow
    ScheduleDateOption.MULTI_DAY -> R.string.schedule_multi_day
}

private fun MultiDayValidationError.messageRes() = when (this) {
    MultiDayValidationError.TOO_FEW_DAYS -> R.string.schedule_multi_day_too_few
    MultiDayValidationError.TOO_MANY_DAYS -> R.string.schedule_multi_day_too_many
    MultiDayValidationError.PAST_DATE -> R.string.schedule_multi_day_past
    MultiDayValidationError.OUTSIDE_BOOKING_WINDOW -> R.string.schedule_multi_day_outside_window
}

private fun DayPart.labelRes() = when (this) {
    DayPart.MORNING -> R.string.schedule_morning
    DayPart.AFTERNOON -> R.string.schedule_afternoon
    DayPart.EVENING -> R.string.schedule_evening
}

private val fullDateFormatter = DateTimeFormatter.ofPattern("EEE, d MMM")
private val shortDateFormatter = DateTimeFormatter.ofPattern("d MMM")
private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")

private val previewOptions = ScheduleDateOptions(
    today = LocalDate.of(2026, 10, 10),
    tomorrow = LocalDate.of(2026, 10, 11),
    multiDayChoices = (0L..6L).map { LocalDate.of(2026, 10, 10).plusDays(it) },
)
private val previewDurations = (1..4).map { ServiceDuration(it, SchedulePrice(it * 13_500L)) }
private val previewSlots = (0..15).map { index ->
    TimeSlot(LocalTime.of(8 + index / 4, (index % 4) * 15), index != 0, 33_500)
}
private val previewPromo = HabitPackPromo(3, 89_700, 27_900)

@Preview(name = "Schedule step 1", widthDp = 411, heightDp = 914, showBackground = true)
@Composable
private fun ScheduleStepOnePreview() = SnabbitTheme {
    ScheduleScreen(ScheduleUiState(dateOptions = previewOptions, durations = previewDurations, promo = previewPromo, isLoading = false), {}, {}, {})
}

@Preview(name = "Schedule step 2", widthDp = 411, heightDp = 914, showBackground = true)
@Composable
private fun ScheduleStepTwoPreview() = SnabbitTheme {
    ScheduleScreen(
        ScheduleUiState(
            dateOptions = previewOptions,
            selectedDateOption = ScheduleDateOption.TODAY,
            selectedDates = setOf(previewOptions.today),
            durations = previewDurations,
            promo = previewPromo,
            isLoading = false,
        ), {}, {}, {},
    )
}

@Preview(name = "Schedule step 3", widthDp = 411, heightDp = 914, showBackground = true)
@Composable
private fun ScheduleStepThreePreview() = SnabbitTheme {
    ScheduleScreen(
        ScheduleUiState(
            dateOptions = previewOptions,
            selectedDateOption = ScheduleDateOption.TOMORROW,
            selectedDates = setOf(previewOptions.tomorrow),
            durations = previewDurations,
            selectedDuration = previewDurations[1],
            slots = previewSlots,
            selectedTime = LocalTime.of(8, 15),
            promo = previewPromo,
            isLoading = false,
        ), {}, {}, {},
    )
}

@Preview(name = "Schedule dark", widthDp = 411, heightDp = 914, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ScheduleDarkPreview() = SnabbitTheme { ScheduleStepThreePreview() }

@Preview(name = "Schedule large text", widthDp = 411, heightDp = 914, fontScale = 2f, showBackground = true)
@Composable
private fun ScheduleLargeTextPreview() = SnabbitTheme { ScheduleStepThreePreview() }
