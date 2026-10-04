package com.snabbit.prototype.mobile.ui.booking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snabbit.prototype.mobile.domain.booking.BookingAvailability
import com.snabbit.prototype.mobile.domain.booking.BookingDraft
import com.snabbit.prototype.mobile.domain.booking.BookingRepository
import com.snabbit.prototype.mobile.domain.booking.BookingType
import com.snabbit.prototype.mobile.ui.common.UiDataError
import com.snabbit.prototype.mobile.ui.common.toUiDataError
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SlotSelectionUiState(
    val bookingType: BookingType = BookingType.INSTANT,
    val dates: List<LocalDate> = emptyList(),
    val selectedDates: Set<LocalDate> = emptySet(),
    val times: List<LocalTime> = emptyList(),
    val selectedTime: LocalTime? = null,
    val isChecking: Boolean = false,
    val availability: BookingAvailability? = null,
    val error: UiDataError? = null,
    val validationError: SlotValidationError? = null,
) {
    val canContinue get() = availability?.allVisitsAvailable == true
}

enum class SlotValidationError { TIME_REQUIRED, ONE_DAY_REQUIRED, MULTI_DAY_COUNT }

class SlotSelectionViewModel(
    private val serviceId: String,
    private val addressId: String,
    private val durationHours: Int,
    private val repository: BookingRepository,
    private val clock: Clock = Clock.system(ZoneId.of("Asia/Kolkata")),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main,
) : ViewModel() {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val today get() = LocalDate.now(clock.withZone(zone))
    private val _uiState = MutableStateFlow(initialState())
    val uiState: StateFlow<SlotSelectionUiState> = _uiState.asStateFlow()

    fun selectType(type: BookingType) = _uiState.update {
        val dates = if (type == BookingType.INSTANT) setOf(today) else emptySet()
        it.copy(bookingType = type, selectedDates = dates, times = availableTimes(dates), selectedTime = null, availability = null, error = null, validationError = null)
    }

    fun toggleDate(date: LocalDate) = _uiState.update { state ->
        val selected = when (state.bookingType) {
            BookingType.MULTI_DAY -> if (date in state.selectedDates) state.selectedDates - date else if (state.selectedDates.size < 7) state.selectedDates + date else state.selectedDates
            BookingType.INSTANT -> setOf(today)
            BookingType.SCHEDULED -> setOf(date)
        }
        state.copy(selectedDates = selected, times = availableTimes(selected), selectedTime = null, availability = null, validationError = null)
    }

    fun selectTime(time: LocalTime) = _uiState.update { it.copy(selectedTime = time, availability = null, validationError = null) }
    fun retry() = checkAvailability()

    fun checkAvailability() {
        val state = _uiState.value
        val validation = validate(state)
        if (validation != null) {
            _uiState.update { it.copy(validationError = validation) }
            return
        }
        viewModelScope.launch(dispatcher) {
            _uiState.update { it.copy(isChecking = true, error = null, validationError = null) }
            runCatching { repository.availability(draft(_uiState.value)) }
                .onSuccess { result -> _uiState.update { it.copy(isChecking = false, availability = result) } }
                .onFailure { failure -> _uiState.update { it.copy(isChecking = false, error = failure.toUiDataError()) } }
        }
    }

    fun draft(): BookingDraft = draft(_uiState.value)

    private fun draft(state: SlotSelectionUiState): BookingDraft {
        val time = requireNotNull(state.selectedTime)
        return BookingDraft(
            serviceId, addressId, state.bookingType, durationHours,
            state.selectedDates.sorted().map { it.atTime(time).atZone(zone).toInstant().toString() },
        )
    }

    private fun validate(state: SlotSelectionUiState): SlotValidationError? = when {
        state.selectedTime == null -> SlotValidationError.TIME_REQUIRED
        state.bookingType == BookingType.MULTI_DAY && state.selectedDates.size !in 2..7 -> SlotValidationError.MULTI_DAY_COUNT
        state.bookingType != BookingType.MULTI_DAY && state.selectedDates.size != 1 -> SlotValidationError.ONE_DAY_REQUIRED
        else -> null
    }

    private fun initialState(): SlotSelectionUiState {
        return SlotSelectionUiState(
            dates = (0L..7L).map { today.plusDays(it) },
            selectedDates = setOf(today),
            times = availableTimes(setOf(today)),
        )
    }

    private fun availableTimes(selectedDates: Set<LocalDate>): List<LocalTime> {
        val now = clock.instant().atZone(zone)
        return (8..(19 - durationHours)).map { LocalTime.of(it, 0) }.filter { time ->
            today !in selectedDates || time.isAfter(now.toLocalTime())
        }
    }

    companion object {
        val dayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d MMM")
        val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("h a")
    }
}
