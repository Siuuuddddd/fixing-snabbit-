package com.snabbit.prototype.mobile.ui.booking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snabbit.prototype.mobile.domain.booking.BookingDraft
import com.snabbit.prototype.mobile.domain.booking.BookingType
import com.snabbit.prototype.mobile.domain.home.GetHabitPackPromoUseCase
import com.snabbit.prototype.mobile.domain.home.HabitPackPromo
import com.snabbit.prototype.mobile.domain.schedule.DayPart
import com.snabbit.prototype.mobile.domain.schedule.GetAvailableSlotsUseCase
import com.snabbit.prototype.mobile.domain.schedule.GetDateOptionsUseCase
import com.snabbit.prototype.mobile.domain.schedule.GetDurationsWithPriceUseCase
import com.snabbit.prototype.mobile.domain.schedule.MultiDayValidationError
import com.snabbit.prototype.mobile.domain.schedule.ScheduleDateOption
import com.snabbit.prototype.mobile.domain.schedule.ScheduleDateOptions
import com.snabbit.prototype.mobile.domain.schedule.ServiceDuration
import com.snabbit.prototype.mobile.domain.schedule.TimeSlot
import com.snabbit.prototype.mobile.domain.schedule.ValidateMultiDaySelectionUseCase
import com.snabbit.prototype.mobile.ui.common.UiDataError
import com.snabbit.prototype.mobile.ui.common.toUiDataError
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

sealed interface ScheduleUiEvent {
    data class DateOptionSelected(val option: ScheduleDateOption) : ScheduleUiEvent
    data class MultiDayToggled(val date: LocalDate) : ScheduleUiEvent
    data object MultiDayMenuToggled : ScheduleUiEvent
    data class DurationSelected(val duration: ServiceDuration) : ScheduleUiEvent
    data class DayPartSelected(val dayPart: DayPart) : ScheduleUiEvent
    data class TimeSelected(val time: LocalTime) : ScheduleUiEvent
    data object PromoAddSelected : ScheduleUiEvent
    data object PromoMessageShown : ScheduleUiEvent
    data object Retry : ScheduleUiEvent
}

data class ScheduleUiState(
    val dateOptions: ScheduleDateOptions? = null,
    val selectedDateOption: ScheduleDateOption? = null,
    val selectedDates: Set<LocalDate> = emptySet(),
    val isMultiDayMenuExpanded: Boolean = false,
    val multiDayError: MultiDayValidationError? = null,
    val durations: List<ServiceDuration> = emptyList(),
    val selectedDuration: ServiceDuration? = null,
    val dayPart: DayPart = DayPart.MORNING,
    val slots: List<TimeSlot> = emptyList(),
    val selectedTime: LocalTime? = null,
    val promo: HabitPackPromo? = null,
    val promoMessagePending: Boolean = false,
    val isLoading: Boolean = true,
    val isLoadingSlots: Boolean = false,
    val error: UiDataError? = null,
) {
    val hasValidDates: Boolean
        get() = when (selectedDateOption) {
            ScheduleDateOption.TODAY, ScheduleDateOption.TOMORROW -> selectedDates.size == 1
            ScheduleDateOption.MULTI_DAY -> multiDayError == null && selectedDates.size in 2..7
            null -> false
        }
    val selectedSlot: TimeSlot? get() = slots.firstOrNull { it.time == selectedTime && it.available }
    val totalPriceMinor: Long?
        get() = selectedSlot?.totalPriceMinor
            ?: selectedDuration?.price?.currentPriceMinor?.times(selectedDates.size.coerceAtLeast(1))
    val canContinue: Boolean get() = hasValidDates && selectedDuration != null && selectedSlot != null
}

class ScheduleViewModel(
    private val serviceId: String,
    private val addressId: String,
    private val getDateOptions: GetDateOptionsUseCase,
    private val getDurationsWithPrice: GetDurationsWithPriceUseCase,
    private val getAvailableSlots: GetAvailableSlotsUseCase,
    private val validateMultiDaySelection: ValidateMultiDaySelectionUseCase,
    private val getHabitPackPromo: GetHabitPackPromoUseCase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main,
) : ViewModel() {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val _uiState = MutableStateFlow(ScheduleUiState())
    val uiState: StateFlow<ScheduleUiState> = _uiState.asStateFlow()
    private var slotsJob: Job? = null

    init {
        loadInitial()
    }

    fun onEvent(event: ScheduleUiEvent) {
        when (event) {
            is ScheduleUiEvent.DateOptionSelected -> selectDateOption(event.option)
            is ScheduleUiEvent.MultiDayToggled -> toggleMultiDay(event.date)
            ScheduleUiEvent.MultiDayMenuToggled -> _uiState.update {
                it.copy(isMultiDayMenuExpanded = !it.isMultiDayMenuExpanded)
            }
            is ScheduleUiEvent.DurationSelected -> {
                _uiState.update { it.copy(selectedDuration = event.duration, selectedTime = null, slots = emptyList(), error = null) }
                loadSlots()
            }
            is ScheduleUiEvent.DayPartSelected -> {
                if (event.dayPart != _uiState.value.dayPart) {
                    _uiState.update { it.copy(dayPart = event.dayPart, selectedTime = null, slots = emptyList(), error = null) }
                    loadSlots()
                }
            }
            is ScheduleUiEvent.TimeSelected -> _uiState.update { state ->
                val slot = state.slots.firstOrNull { it.time == event.time }
                if (slot?.available == true) state.copy(selectedTime = event.time) else state
            }
            ScheduleUiEvent.PromoAddSelected -> _uiState.update { it.copy(promoMessagePending = true) }
            ScheduleUiEvent.PromoMessageShown -> _uiState.update { it.copy(promoMessagePending = false) }
            ScheduleUiEvent.Retry -> if (_uiState.value.selectedDuration == null) loadInitial() else loadSlots()
        }
    }

    fun draftOrNull(): BookingDraft? {
        val state = _uiState.value
        val duration = state.selectedDuration ?: return null
        val time = state.selectedTime ?: return null
        if (!state.canContinue) return null
        return BookingDraft(
            serviceId = serviceId,
            addressId = addressId,
            bookingType = if (state.selectedDates.size > 1) BookingType.MULTI_DAY else BookingType.SCHEDULED,
            durationHours = duration.hours,
            startsAt = state.selectedDates.sorted().map { date ->
                date.atTime(time).atZone(zone).toInstant().toString()
            },
        )
    }

    private fun loadInitial() {
        viewModelScope.launch(dispatcher) {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val loaded = supervisorScope {
                    val durations = async { getDurationsWithPrice(serviceId) }
                    val promo = async { getHabitPackPromo() }
                    durations.await() to promo.await()
                }
                _uiState.update {
                    it.copy(
                        dateOptions = getDateOptions(),
                        durations = loaded.first,
                        promo = loaded.second,
                        isLoading = false,
                    )
                }
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Throwable) {
                _uiState.update { it.copy(isLoading = false, error = failure.toUiDataError()) }
            }
        }
    }

    private fun selectDateOption(option: ScheduleDateOption) {
        slotsJob?.cancel()
        _uiState.update { state ->
            val options = state.dateOptions ?: return@update state
            val dates = when (option) {
                ScheduleDateOption.TODAY -> setOf(options.today)
                ScheduleDateOption.TOMORROW -> setOf(options.tomorrow)
                ScheduleDateOption.MULTI_DAY -> emptySet()
            }
            state.copy(
                selectedDateOption = option,
                selectedDates = dates,
                isMultiDayMenuExpanded = option == ScheduleDateOption.MULTI_DAY,
                multiDayError = if (option == ScheduleDateOption.MULTI_DAY) MultiDayValidationError.TOO_FEW_DAYS else null,
                selectedDuration = null,
                slots = emptyList(),
                selectedTime = null,
                error = null,
            )
        }
    }

    private fun toggleMultiDay(date: LocalDate) {
        slotsJob?.cancel()
        _uiState.update { state ->
            val choices = state.dateOptions?.multiDayChoices.orEmpty()
            if (date !in choices) return@update state
            val dates = if (date in state.selectedDates) state.selectedDates - date else state.selectedDates + date
            val validation = validateMultiDaySelection(dates)
            state.copy(
                selectedDates = dates,
                multiDayError = validation.error,
                selectedDuration = null,
                slots = emptyList(),
                selectedTime = null,
                error = null,
            )
        }
    }

    private fun loadSlots() {
        slotsJob?.cancel()
        val state = _uiState.value
        val duration = state.selectedDuration ?: return
        if (!state.hasValidDates) return
        slotsJob = viewModelScope.launch(dispatcher) {
            _uiState.update { it.copy(isLoadingSlots = true, error = null) }
            try {
                val slots = getAvailableSlots(
                    serviceId = serviceId,
                    addressId = addressId,
                    dates = state.selectedDates.sorted(),
                    durationHours = duration.hours,
                    dayPart = state.dayPart,
                )
                _uiState.update { it.copy(slots = slots, isLoadingSlots = false) }
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Throwable) {
                _uiState.update { it.copy(slots = emptyList(), isLoadingSlots = false, error = failure.toUiDataError()) }
            }
        }
    }
}
