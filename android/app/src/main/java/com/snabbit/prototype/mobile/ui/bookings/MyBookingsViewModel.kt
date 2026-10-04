package com.snabbit.prototype.mobile.ui.bookings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snabbit.prototype.mobile.domain.booking.Booking
import com.snabbit.prototype.mobile.domain.booking.BookingStatus
import com.snabbit.prototype.mobile.domain.bookings.BookingListRepository
import com.snabbit.prototype.mobile.ui.common.UiDataError
import com.snabbit.prototype.mobile.ui.common.toUiDataError
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class BookingListFilter { UPCOMING, PAST }

data class MyBookingsUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val bookings: List<Booking> = emptyList(),
    val filter: BookingListFilter = BookingListFilter.UPCOMING,
    val error: UiDataError? = null,
) {
    val visibleBookings: List<Booking> get() = bookings.filter { booking ->
        val past = booking.status in setOf(BookingStatus.COMPLETED, BookingStatus.CANCELLED, BookingStatus.EXPIRED, BookingStatus.WORKER_UNAVAILABLE)
        if (filter == BookingListFilter.PAST) past else !past
    }
}

class MyBookingsViewModel(
    private val repository: BookingListRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MyBookingsUiState())
    val uiState: StateFlow<MyBookingsUiState> = _uiState.asStateFlow()
    init { load(false) }

    fun refresh() = load(true)
    fun retry() = load(false)
    fun selectFilter(filter: BookingListFilter) = _uiState.update { it.copy(filter = filter) }

    private fun load(refreshing: Boolean) = viewModelScope.launch(dispatcher) {
        _uiState.update { it.copy(isLoading = !refreshing && it.bookings.isEmpty(), isRefreshing = refreshing, error = null) }
        runCatching { repository.bookings() }
            .onSuccess { bookings -> _uiState.update { it.copy(isLoading = false, isRefreshing = false, bookings = bookings) } }
            .onFailure { failure -> _uiState.update { it.copy(isLoading = false, isRefreshing = false, error = failure.toUiDataError()) } }
    }
}
