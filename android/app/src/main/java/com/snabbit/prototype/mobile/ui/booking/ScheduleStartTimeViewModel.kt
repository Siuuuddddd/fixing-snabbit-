package com.snabbit.prototype.mobile.ui.booking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snabbit.prototype.mobile.domain.booking.BookingDraft
import com.snabbit.prototype.mobile.domain.booking.BookingType
import com.snabbit.prototype.mobile.domain.home.GetHabitPackPromoUseCase
import com.snabbit.prototype.mobile.domain.home.HabitPackPromo
import com.snabbit.prototype.mobile.domain.schedule.DayPart
import com.snabbit.prototype.mobile.domain.schedule.GetAvailableSlotsUseCase
import com.snabbit.prototype.mobile.domain.schedule.GetDurationsWithPriceUseCase
import com.snabbit.prototype.mobile.domain.schedule.ServiceDuration
import com.snabbit.prototype.mobile.domain.schedule.TimeSlot
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

sealed interface ScheduleStartTimeEvent {
    data class DayPartSelected(val dayPart: DayPart) : ScheduleStartTimeEvent
    data class TimeSelected(val time: LocalTime) : ScheduleStartTimeEvent
    data object PromoAddSelected : ScheduleStartTimeEvent
    data object PromoMessageShown : ScheduleStartTimeEvent
    data object Retry : ScheduleStartTimeEvent
}

data class ScheduleStartTimeUiState(
    val dates: List<LocalDate> = emptyList(),
    val duration: ServiceDuration? = null,
    val dayPart: DayPart = DayPart.MORNING,
    val slots: List<TimeSlot> = emptyList(),
    val selectedTime: LocalTime? = null,
    val promo: HabitPackPromo? = null,
    val promoMessagePending: Boolean = false,
    val isLoading: Boolean = true,
    val error: UiDataError? = null,
) {
    val selectedSlot: TimeSlot? get() = slots.firstOrNull { it.time == selectedTime && it.available }
    val totalPriceMinor: Long? get() = selectedSlot?.totalPriceMinor ?: duration?.price?.currentPriceMinor?.times(dates.size)
    val canContinue: Boolean get() = dates.isNotEmpty() && duration != null && selectedSlot != null
}

class ScheduleStartTimeViewModel(
    private val serviceId: String,
    private val addressId: String,
    dates: List<String>,
    private val durationHours: Int,
    private val getDurationsWithPrice: GetDurationsWithPriceUseCase,
    private val getAvailableSlots: GetAvailableSlotsUseCase,
    private val getHabitPackPromo: GetHabitPackPromoUseCase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main,
) : ViewModel() {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val parsedDates = dates.map(LocalDate::parse).distinct().sorted()
    private val _uiState = MutableStateFlow(ScheduleStartTimeUiState(dates = parsedDates))
    val uiState: StateFlow<ScheduleStartTimeUiState> = _uiState.asStateFlow()
    private var slotsJob: Job? = null

    init { load() }

    fun onEvent(event: ScheduleStartTimeEvent) {
        when (event) {
            is ScheduleStartTimeEvent.DayPartSelected -> if (event.dayPart != _uiState.value.dayPart) {
                _uiState.update { it.copy(dayPart = event.dayPart, selectedTime = null, slots = emptyList(), error = null) }
                loadSlots()
            }
            is ScheduleStartTimeEvent.TimeSelected -> _uiState.update { state ->
                if (state.slots.any { it.time == event.time && it.available }) state.copy(selectedTime = event.time) else state
            }
            ScheduleStartTimeEvent.PromoAddSelected -> _uiState.update { it.copy(promoMessagePending = true) }
            ScheduleStartTimeEvent.PromoMessageShown -> _uiState.update { it.copy(promoMessagePending = false) }
            ScheduleStartTimeEvent.Retry -> load()
        }
    }

    fun draftOrNull(): BookingDraft? {
        val state = _uiState.value
        val duration = state.duration ?: return null
        val time = state.selectedTime ?: return null
        if (!state.canContinue) return null
        return BookingDraft(
            serviceId = serviceId,
            addressId = addressId,
            bookingType = if (state.dates.size > 1) BookingType.MULTI_DAY else BookingType.SCHEDULED,
            durationHours = duration.hours,
            startsAt = state.dates.map { it.atTime(time).atZone(zone).toInstant().toString() },
        )
    }

    private fun load() {
        slotsJob?.cancel()
        viewModelScope.launch(dispatcher) {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val loaded = supervisorScope {
                    val duration = async { getDurationsWithPrice(serviceId).first { it.hours == durationHours } }
                    val promo = async { getHabitPackPromo() }
                    duration.await() to promo.await()
                }
                _uiState.update { it.copy(duration = loaded.first, promo = loaded.second, isLoading = false) }
                loadSlots()
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Throwable) {
                _uiState.update { it.copy(isLoading = false, error = failure.toUiDataError()) }
            }
        }
    }

    private fun loadSlots() {
        slotsJob?.cancel()
        val state = _uiState.value
        if (state.duration == null || state.dates.isEmpty()) return
        slotsJob = viewModelScope.launch(dispatcher) {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val slots = getAvailableSlots(serviceId, addressId, state.dates, durationHours, state.dayPart)
                _uiState.update { it.copy(slots = slots, isLoading = false) }
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Throwable) {
                _uiState.update { it.copy(slots = emptyList(), isLoading = false, error = failure.toUiDataError()) }
            }
        }
    }
}
