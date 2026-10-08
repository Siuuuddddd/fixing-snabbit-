package com.snabbit.prototype.mobile.ui.booking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snabbit.prototype.mobile.domain.home.GetHabitPackPromoUseCase
import com.snabbit.prototype.mobile.domain.home.HabitPackPromo
import com.snabbit.prototype.mobile.domain.schedule.GetDateOptionsUseCase
import com.snabbit.prototype.mobile.domain.schedule.GetDurationsWithPriceUseCase
import com.snabbit.prototype.mobile.domain.schedule.MultiDayValidationError
import com.snabbit.prototype.mobile.domain.schedule.ScheduleDateOption
import com.snabbit.prototype.mobile.domain.schedule.ScheduleDateOptions
import com.snabbit.prototype.mobile.domain.schedule.ServiceDuration
import com.snabbit.prototype.mobile.domain.schedule.ValidateMultiDaySelectionUseCase
import com.snabbit.prototype.mobile.ui.common.UiDataError
import com.snabbit.prototype.mobile.ui.common.toUiDataError
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.async

sealed interface ScheduleDateDurationEvent {
    data class DateOptionSelected(val option: ScheduleDateOption) : ScheduleDateDurationEvent
    data class MultiDayToggled(val date: LocalDate) : ScheduleDateDurationEvent
    data object MultiDayMenuToggled : ScheduleDateDurationEvent
    data object MultiDayApplied : ScheduleDateDurationEvent
    data class DurationSelected(val duration: ServiceDuration) : ScheduleDateDurationEvent
    data object Retry : ScheduleDateDurationEvent
}

data class ScheduleSelection(val dates: List<String>, val durationHours: Int)

data class ScheduleDateDurationUiState(
    val dateOptions: ScheduleDateOptions? = null,
    val selectedDateOption: ScheduleDateOption? = null,
    val selectedDates: Set<LocalDate> = emptySet(),
    val pendingMultiDates: Set<LocalDate> = emptySet(),
    val isMultiDayMenuExpanded: Boolean = false,
    val multiDayError: MultiDayValidationError? = null,
    val durations: List<ServiceDuration> = emptyList(),
    val selectedDuration: ServiceDuration? = null,
    val promo: HabitPackPromo? = null,
    val isLoading: Boolean = true,
    val error: UiDataError? = null,
) {
    val hasConfirmedDates: Boolean
        get() = when (selectedDateOption) {
            ScheduleDateOption.TODAY, ScheduleDateOption.TOMORROW -> selectedDates.size == 1
            ScheduleDateOption.MULTI_DAY -> selectedDates.size in 2..7 && !isMultiDayMenuExpanded
            null -> false
        }
}

class ScheduleDateDurationViewModel(
    private val getDateOptions: GetDateOptionsUseCase,
    private val getDurationsWithPrice: GetDurationsWithPriceUseCase,
    private val validateMultiDaySelection: ValidateMultiDaySelectionUseCase,
    private val getHabitPackPromo: GetHabitPackPromoUseCase,
    private val serviceId: String,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ScheduleDateDurationUiState())
    val uiState: StateFlow<ScheduleDateDurationUiState> = _uiState.asStateFlow()
    private val navigationChannel = Channel<ScheduleSelection>(Channel.BUFFERED)
    val navigation = navigationChannel.receiveAsFlow()
    private var lastNavigation: ScheduleSelection? = null

    init { load() }

    fun onEvent(event: ScheduleDateDurationEvent) {
        when (event) {
            is ScheduleDateDurationEvent.DateOptionSelected -> selectDateOption(event.option)
            is ScheduleDateDurationEvent.MultiDayToggled -> toggleMultiDay(event.date)
            ScheduleDateDurationEvent.MultiDayMenuToggled -> _uiState.update {
                it.copy(isMultiDayMenuExpanded = !it.isMultiDayMenuExpanded)
            }
            ScheduleDateDurationEvent.MultiDayApplied -> applyMultiDay()
            is ScheduleDateDurationEvent.DurationSelected -> selectDuration(event.duration)
            ScheduleDateDurationEvent.Retry -> load()
        }
    }

    private fun load() {
        viewModelScope.launch(dispatcher) {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val loaded = supervisorScope {
                    val durations = async { getDurationsWithPrice(serviceId) }
                    val promo = async { getHabitPackPromo() }
                    durations.await() to promo.await()
                }
                _uiState.update {
                    it.copy(dateOptions = getDateOptions(), durations = loaded.first, promo = loaded.second, isLoading = false)
                }
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Throwable) {
                _uiState.update { it.copy(isLoading = false, error = failure.toUiDataError()) }
            }
        }
    }

    private fun selectDateOption(option: ScheduleDateOption) {
        _uiState.update { state ->
            val options = state.dateOptions ?: return@update state
            val dates = when (option) {
                ScheduleDateOption.TODAY -> setOf(options.today)
                ScheduleDateOption.TOMORROW -> setOf(options.tomorrow)
                ScheduleDateOption.MULTI_DAY -> if (state.selectedDateOption == ScheduleDateOption.MULTI_DAY) {
                    state.pendingMultiDates
                } else {
                    emptySet()
                }
            }
            val confirmed = if (option == ScheduleDateOption.MULTI_DAY) emptySet() else dates
            val changed = option != state.selectedDateOption || confirmed != state.selectedDates
            if (changed) lastNavigation = null
            state.copy(
                selectedDateOption = option,
                selectedDates = confirmed,
                pendingMultiDates = dates,
                isMultiDayMenuExpanded = option == ScheduleDateOption.MULTI_DAY,
                multiDayError = if (option == ScheduleDateOption.MULTI_DAY) {
                    validateMultiDaySelection(dates).error
                } else null,
                selectedDuration = if (changed) null else state.selectedDuration,
                error = null,
            )
        }
        emitSelectionIfReady()
    }

    private fun toggleMultiDay(date: LocalDate) {
        _uiState.update { state ->
            if (date !in state.dateOptions?.multiDayChoices.orEmpty()) return@update state
            val dates = if (date in state.pendingMultiDates) state.pendingMultiDates - date else state.pendingMultiDates + date
            state.copy(pendingMultiDates = dates, multiDayError = validateMultiDaySelection(dates).error)
        }
    }

    private fun applyMultiDay() {
        val state = _uiState.value
        val validation = validateMultiDaySelection(state.pendingMultiDates)
        if (!validation.isValid) {
            _uiState.update { it.copy(multiDayError = validation.error) }
            return
        }
        val changed = state.selectedDates != state.pendingMultiDates
        if (changed) lastNavigation = null
        _uiState.update {
            it.copy(
                selectedDates = it.pendingMultiDates,
                isMultiDayMenuExpanded = false,
                multiDayError = null,
                selectedDuration = if (changed) null else it.selectedDuration,
            )
        }
        emitSelectionIfReady()
    }

    private fun selectDuration(duration: ServiceDuration) {
        val changed = _uiState.value.selectedDuration?.hours != duration.hours
        if (changed) lastNavigation = null
        _uiState.update { it.copy(selectedDuration = duration, error = null) }
        emitSelectionIfReady()
    }

    private fun emitSelectionIfReady() {
        val state = _uiState.value
        val duration = state.selectedDuration ?: return
        if (!state.hasConfirmedDates) return
        val selection = ScheduleSelection(state.selectedDates.sorted().map(LocalDate::toString), duration.hours)
        if (selection == lastNavigation) return
        lastNavigation = selection
        navigationChannel.trySend(selection)
    }
}
