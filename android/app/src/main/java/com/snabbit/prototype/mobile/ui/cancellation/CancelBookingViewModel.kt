package com.snabbit.prototype.mobile.ui.cancellation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snabbit.prototype.mobile.domain.booking.Booking
import com.snabbit.prototype.mobile.domain.cancellation.CancellationQuote
import com.snabbit.prototype.mobile.domain.cancellation.CancellationRepository
import com.snabbit.prototype.mobile.ui.common.UiDataError
import com.snabbit.prototype.mobile.ui.common.toUiDataError
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CancelBookingUiState(
    val quote: CancellationQuote? = null,
    val reason: String = "",
    val isLoading: Boolean = true,
    val isCancelling: Boolean = false,
    val cancelledBooking: Booking? = null,
    val error: UiDataError? = null,
)

class CancelBookingViewModel(
    private val bookingId: String,
    private val repository: CancellationRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CancelBookingUiState())
    val uiState: StateFlow<CancelBookingUiState> = _uiState.asStateFlow()
    init { loadQuote() }

    fun retry() = loadQuote()
    fun updateReason(reason: String) = _uiState.update { it.copy(reason = reason.take(240)) }

    fun confirmCancellation() {
        val state = _uiState.value
        if (state.isCancelling || state.quote == null) return
        viewModelScope.launch(dispatcher) {
            _uiState.update { it.copy(isCancelling = true, error = null) }
            runCatching { repository.cancel(bookingId, state.reason) }
                .onSuccess { booking -> _uiState.update { it.copy(isCancelling = false, cancelledBooking = booking) } }
                .onFailure { failure -> _uiState.update { it.copy(isCancelling = false, error = failure.toUiDataError()) } }
        }
    }

    private fun loadQuote() = viewModelScope.launch(dispatcher) {
        _uiState.update { it.copy(isLoading = true, error = null) }
        runCatching { repository.quote(bookingId) }
            .onSuccess { quote -> _uiState.update { it.copy(isLoading = false, quote = quote) } }
            .onFailure { failure -> _uiState.update { it.copy(isLoading = false, error = failure.toUiDataError()) } }
    }
}
