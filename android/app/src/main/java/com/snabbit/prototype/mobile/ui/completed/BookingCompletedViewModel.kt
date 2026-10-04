package com.snabbit.prototype.mobile.ui.completed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snabbit.prototype.mobile.domain.booking.Booking
import com.snabbit.prototype.mobile.domain.booking.BookingRepository
import com.snabbit.prototype.mobile.domain.rating.RatingRepository
import com.snabbit.prototype.mobile.domain.rating.WorkerRating
import com.snabbit.prototype.mobile.ui.common.UiDataError
import com.snabbit.prototype.mobile.ui.common.toUiDataError
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BookingCompletedUiState(
    val booking: Booking? = null,
    val stars: Int = 0,
    val comment: String = "",
    val isLoading: Boolean = true,
    val isSubmitting: Boolean = false,
    val rating: WorkerRating? = null,
    val validationError: Boolean = false,
    val error: UiDataError? = null,
)

class BookingCompletedViewModel(
    private val bookingId: String,
    private val visitIndex: Int,
    private val workerId: String,
    private val bookingRepository: BookingRepository,
    private val ratingRepository: RatingRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main,
) : ViewModel() {
    private val _uiState = MutableStateFlow(BookingCompletedUiState())
    val uiState: StateFlow<BookingCompletedUiState> = _uiState.asStateFlow()
    init { load() }

    fun retry() = load()
    fun selectStars(stars: Int) = _uiState.update { it.copy(stars = stars.coerceIn(1, 5), validationError = false) }
    fun updateComment(comment: String) = _uiState.update { it.copy(comment = comment.take(500)) }

    fun submitRating() {
        val state = _uiState.value
        if (state.stars !in 1..5) {
            _uiState.update { it.copy(validationError = true) }
            return
        }
        if (state.isSubmitting || state.rating != null) return
        viewModelScope.launch(dispatcher) {
            _uiState.update { it.copy(isSubmitting = true, error = null) }
            runCatching {
                ratingRepository.rateWorker(workerId, bookingId, visitIndex, state.stars, state.comment)
            }.onSuccess { rating -> _uiState.update { it.copy(isSubmitting = false, rating = rating) } }
                .onFailure { failure -> _uiState.update { it.copy(isSubmitting = false, error = failure.toUiDataError()) } }
        }
    }

    private fun load() = viewModelScope.launch(dispatcher) {
        _uiState.update { it.copy(isLoading = true, error = null) }
        runCatching { bookingRepository.booking(bookingId) }
            .onSuccess { booking -> _uiState.update { it.copy(isLoading = false, booking = booking) } }
            .onFailure { failure -> _uiState.update { it.copy(isLoading = false, error = failure.toUiDataError()) } }
    }
}
