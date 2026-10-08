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
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.snabbit.prototype.mobile.R
import com.snabbit.prototype.mobile.domain.schedule.MultiDayValidationError
import com.snabbit.prototype.mobile.domain.schedule.ScheduleDateOption
import com.snabbit.prototype.mobile.domain.schedule.ScheduleDateOptions
import com.snabbit.prototype.mobile.domain.schedule.SchedulePrice
import com.snabbit.prototype.mobile.domain.schedule.ServiceDuration
import com.snabbit.prototype.mobile.ui.common.DataErrorPanel
import com.snabbit.prototype.mobile.ui.common.formatInrMinor
import com.snabbit.prototype.mobile.ui.theme.SnabbitSpacing
import com.snabbit.prototype.mobile.ui.theme.SnabbitTextStyles
import com.snabbit.prototype.mobile.ui.theme.SnabbitTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleDateDurationScreen(
    state: ScheduleDateDurationUiState,
    onBack: () -> Unit,
    onEvent: (ScheduleDateDurationEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
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
    ) { padding ->
        when {
            state.isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.error != null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                DataErrorPanel(state.error, onRetry = { onEvent(ScheduleDateDurationEvent.Retry) })
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(SnabbitSpacing.standard),
                verticalArrangement = Arrangement.spacedBy(SnabbitSpacing.standard),
            ) {
                item { DateSelectionCard(state, onEvent) }
                if (state.hasConfirmedDates) item { DurationSelectionCard(state, onEvent) }
                if (state.hasConfirmedDates && state.selectedDuration == null) {
                    item {
                        Text(
                            stringResource(R.string.schedule_auto_continue_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = SnabbitSpacing.extraSmall),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DateSelectionCard(state: ScheduleDateDurationUiState, onEvent: (ScheduleDateDurationEvent) -> Unit) {
    ScheduleCard {
        Text(stringResource(R.string.schedule_select_date), style = SnabbitTextStyles.SectionHeading)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(SnabbitSpacing.small)) {
            ScheduleDateOption.entries.forEach { option ->
                SelectionTile(
                    selected = state.selectedDateOption == option,
                    onClick = { onEvent(ScheduleDateDurationEvent.DateOptionSelected(option)) },
                    modifier = Modifier.weight(1f),
                ) {
                    Box(Modifier.fillMaxSize().padding(horizontal = SnabbitSpacing.extraSmall), contentAlignment = Alignment.Center) {
                        Text(stringResource(option.labelRes()), fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                    }
                }
            }
        }
        if (state.selectedDateOption == ScheduleDateOption.MULTI_DAY) {
            MultiDayDropdown(state, onEvent)
            state.multiDayError?.let {
                Text(stringResource(it.messageRes()), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            if (state.pendingMultiDates.isNotEmpty()) {
                Text(
                    stringResource(R.string.schedule_selected_days_summary, state.pendingMultiDates.sorted().joinToString { it.format(shortDateFormatter) }),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun MultiDayDropdown(state: ScheduleDateDurationUiState, onEvent: (ScheduleDateDurationEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(SnabbitSpacing.small)) {
        Surface(
            onClick = { onEvent(ScheduleDateDurationEvent.MultiDayMenuToggled) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Row(Modifier.fillMaxSize().padding(horizontal = SnabbitSpacing.standard), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.schedule_choose_multiple_days), Modifier.weight(1f))
                Icon(
                    if (state.isMultiDayMenuExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    stringResource(R.string.schedule_toggle_day_menu),
                )
            }
        }
        AnimatedVisibility(state.isMultiDayMenuExpanded) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                state.dateOptions?.multiDayChoices.orEmpty().forEach { date ->
                    val selected = date in state.pendingMultiDates
                    Surface(onClick = { onEvent(ScheduleDateDurationEvent.MultiDayToggled(date)) }) {
                        Row(Modifier.fillMaxWidth().padding(SnabbitSpacing.medium), verticalAlignment = Alignment.CenterVertically) {
                            Text(date.format(fullDateFormatter), Modifier.weight(1f))
                            Surface(
                                modifier = Modifier.size(24.dp),
                                shape = RoundedCornerShape(6.dp),
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                            ) { if (selected) Icon(Icons.Outlined.Check, null, tint = MaterialTheme.colorScheme.onPrimary) }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                Button(
                    onClick = { onEvent(ScheduleDateDurationEvent.MultiDayApplied) },
                    enabled = state.multiDayError == null,
                    modifier = Modifier.fillMaxWidth().padding(SnabbitSpacing.medium),
                ) { Text(stringResource(R.string.action_done)) }
            }
        }
    }
}

@Composable
private fun DurationSelectionCard(state: ScheduleDateDurationUiState, onEvent: (ScheduleDateDurationEvent) -> Unit) {
    ScheduleCard(contentPadding = PaddingValues(vertical = SnabbitSpacing.standard)) {
        Text(
            stringResource(R.string.schedule_select_duration),
            style = SnabbitTextStyles.SectionHeading,
            modifier = Modifier.padding(horizontal = SnabbitSpacing.standard),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = SnabbitSpacing.standard),
            horizontalArrangement = Arrangement.spacedBy(SnabbitSpacing.medium),
        ) {
            items(state.durations, key = { it.hours }) { duration ->
                SelectionTile(
                    selected = state.selectedDuration?.hours == duration.hours,
                    onClick = { onEvent(ScheduleDateDurationEvent.DurationSelected(duration)) },
                    modifier = Modifier.width(140.dp).height(112.dp),
                ) {
                    Column(Modifier.fillMaxSize().padding(SnabbitSpacing.standard), verticalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            pluralStringResource(R.plurals.schedule_duration_hours, duration.hours, duration.hours),
                            fontWeight = FontWeight.Bold,
                        )
                        Text(formatInrMinor(duration.price.currentPriceMinor), style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
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

private val fullDateFormatter = DateTimeFormatter.ofPattern("EEE, d MMM")
private val shortDateFormatter = DateTimeFormatter.ofPattern("d MMM")

private val previewOptions = ScheduleDateOptions(
    LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 11),
    (0L..6L).map { LocalDate.of(2026, 10, 10).plusDays(it) },
)
private val previewDurations = (1..4).map { ServiceDuration(it, SchedulePrice(it * 13_500L)) }

@Preview(name = "Schedule date and duration", widthDp = 411, heightDp = 914, showBackground = true)
@Preview(name = "Schedule date and duration dark", widthDp = 411, heightDp = 914, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ScheduleDateDurationPreview() = SnabbitTheme {
    ScheduleDateDurationScreen(
        ScheduleDateDurationUiState(
            dateOptions = previewOptions,
            selectedDateOption = ScheduleDateOption.TODAY,
            selectedDates = setOf(previewOptions.today),
            durations = previewDurations,
            isLoading = false,
        ), {}, {},
    )
}
