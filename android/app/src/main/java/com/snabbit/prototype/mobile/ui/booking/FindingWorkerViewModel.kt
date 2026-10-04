package com.snabbit.prototype.mobile.ui.booking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snabbit.prototype.mobile.domain.booking.Booking
import com.snabbit.prototype.mobile.domain.booking.BookingPayment
import com.snabbit.prototype.mobile.domain.booking.BookingRepository
import com.snabbit.prototype.mobile.domain.booking.BookingStatus
import com.snabbit.prototype.mobile.ui.common.UiDataError
import com.snabbit.prototype.mobile.ui.common.toUiDataError
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class FindingWorkerUiState(
    val isLoading: Boolean = true,
    val isCancelling: Boolean = false,
    val booking: Booking? = null,
    val payment: BookingPayment? = null,
    val paymentUnavailable: Boolean = false,
    val error: UiDataError? = null,
)

class FindingWorkerViewModel(
    private val bookingId: String,
    private val walletId: String?,
    private val useWallet: Boolean,
    private val repository: BookingRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main,
    private val pollIntervalMillis: Long = 3_000,
    private val paymentIdempotencyKey: String = UUID.randomUUID().toString(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(FindingWorkerUiState())
    val uiState: StateFlow<FindingWorkerUiState> = _uiState.asStateFlow()
    private var paymentRequested = false

    init { poll() }
    fun retry() { if (!_uiState.value.isLoading) poll() }

    fun cancel() {
        if (_uiState.value.isCancelling) return
        viewModelScope.launch(dispatcher) {
            _uiState.update { it.copy(isCancelling = true, error = null) }
            runCatching { repository.cancelHold(bookingId) }
                .onSuccess { booking -> _uiState.update { it.copy(isCancelling = false, booking = booking) } }
                .onFailure { failure -> _uiState.update { it.copy(isCancelling = false, error = failure.toUiDataError()) } }
        }
    }

    private fun poll() = viewModelScope.launch(dispatcher) {
        _uiState.update { it.copy(isLoading = it.booking == null, error = null) }
        while (isActive) {
            val booking = runCatching { repository.booking(bookingId) }
                .onFailure { failure -> _uiState.update { it.copy(isLoading = false, error = failure.toUiDataError()) } }
                .getOrNull() ?: return@launch
            _uiState.update { it.copy(isLoading = false, booking = booking, error = null) }
            if (booking.status == BookingStatus.AWAITING_PAYMENT) startPayment()
            if (booking.status in TERMINAL_STATUSES) return@launch
            delay(pollIntervalMillis)
        }
    }

    private suspend fun startPayment() {
        if (paymentRequested) return
        paymentRequested = true
        val id = walletId
        if (id == null) {
            _uiState.update { it.copy(paymentUnavailable = true) }
            return
        }
        runCatching { repository.startPayment(bookingId, id, useWallet, paymentIdempotencyKey) }
            .onSuccess { payment -> _uiState.update { it.copy(payment = payment) } }
            .onFailure { failure ->
                paymentRequested = false
                _uiState.update { it.copy(error = failure.toUiDataError()) }
            }
    }

    private companion object {
        val TERMINAL_STATUSES = setOf(
            BookingStatus.CONFIRMED, BookingStatus.WORKER_UNAVAILABLE, BookingStatus.EXPIRED,
            BookingStatus.CANCELLED, BookingStatus.COMPLETED,
        )
    }
}
