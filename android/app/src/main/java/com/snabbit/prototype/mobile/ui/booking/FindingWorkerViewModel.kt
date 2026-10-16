package com.snabbit.prototype.mobile.ui.booking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snabbit.prototype.mobile.domain.booking.Booking
import com.snabbit.prototype.mobile.domain.booking.BookingPayment
import com.snabbit.prototype.mobile.domain.booking.BookingRepository
import com.snabbit.prototype.mobile.domain.booking.BookingStatus
import com.snabbit.prototype.mobile.domain.booking.WorkerOption
import com.snabbit.prototype.mobile.ui.common.UiDataError
import com.snabbit.prototype.mobile.ui.common.toUiDataError
import java.time.Duration
import java.time.Instant
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
    val isSubmitting: Boolean = false,
    val booking: Booking? = null,
    val payment: BookingPayment? = null,
    val activeVisitIndex: Int = 0,
    val favourites: List<WorkerOption> = emptyList(),
    val others: List<WorkerOption> = emptyList(),
    val selectedWorkers: Map<Int, String> = emptyMap(),
    val homeContactName: String = "",
    val homeContactPhone: String = "",
    val otp: String = "",
    val otpMessage: String? = null,
    val remainingSeconds: Long = 0,
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

    init { poll() }
    fun retry() = poll()
    fun setHomeName(value: String) = _uiState.update { it.copy(homeContactName = value.take(60)) }
    fun setHomePhone(value: String) = _uiState.update { it.copy(homeContactPhone = value.filter(Char::isDigit).takeLast(10)) }
    fun setOtp(value: String) = _uiState.update { it.copy(otp = value.filter(Char::isDigit).take(6), otpMessage = null) }

    fun selectWorker(workerId: String) {
        val visit = _uiState.value.activeVisitIndex
        if (_uiState.value.isSubmitting) return
        viewModelScope.launch(dispatcher) {
            _uiState.update { it.copy(isSubmitting = true, error = null) }
            runCatching { repository.selectWorker(bookingId, visit, workerId) }
                .onSuccess { booking ->
                    val selected = _uiState.value.selectedWorkers + (visit to workerId)
                    val next = booking.visits.firstOrNull { it.workerId == null }?.visitIndex
                    _uiState.update { it.copy(isSubmitting = false, booking = booking, selectedWorkers = selected) }
                    if (next != null) loadOptions(next)
                }
                .onFailure { failure -> _uiState.update { it.copy(isSubmitting = false, error = failure.toUiDataError()) } }
        }
    }

    fun pay() {
        val state = _uiState.value
        if (state.isSubmitting || state.booking?.status != BookingStatus.AWAITING_PAYMENT) return
        val id = walletId ?: run { _uiState.update { it.copy(paymentUnavailable = true) }; return }
        viewModelScope.launch(dispatcher) {
            _uiState.update { it.copy(isSubmitting = true, error = null) }
            runCatching {
                repository.updateHomeContact(bookingId, state.homeContactName.ifBlank { null }, state.homeContactPhone.ifBlank { null })
                repository.startPayment(bookingId, id, useWallet, paymentIdempotencyKey)
            }.onSuccess { payment -> _uiState.update { it.copy(isSubmitting = false, payment = payment) } }
                .onFailure { failure -> _uiState.update { it.copy(isSubmitting = false, error = failure.toUiDataError()) } }
        }
    }

    fun verifyOtp() {
        val state = _uiState.value
        val visit = state.booking?.visits?.firstOrNull { it.jobStatus == "ARRIVED" } ?: return
        if (state.otp.length != 6 || state.isSubmitting) return
        viewModelScope.launch(dispatcher) {
            _uiState.update { it.copy(isSubmitting = true, otpMessage = null) }
            runCatching { repository.verifyArrivalOtp(bookingId, visit.visitIndex, state.otp) }
                .onSuccess { result -> _uiState.update { it.copy(isSubmitting = false, otpMessage = result.status) } }
                .onFailure { failure -> _uiState.update { it.copy(isSubmitting = false, error = failure.toUiDataError()) } }
        }
    }

    fun resendOtp() {
        val visit = _uiState.value.booking?.visits?.firstOrNull { it.jobStatus == "ARRIVED" } ?: return
        viewModelScope.launch(dispatcher) {
            runCatching { repository.resendArrivalOtp(bookingId, visit.visitIndex) }
                .onSuccess { result -> _uiState.update { it.copy(otp = "", otpMessage = result.status) } }
                .onFailure { failure -> _uiState.update { it.copy(error = failure.toUiDataError()) } }
        }
    }

    fun cancel() {
        if (_uiState.value.isCancelling) return
        viewModelScope.launch(dispatcher) {
            _uiState.update { it.copy(isCancelling = true, error = null) }
            runCatching { repository.cancelHold(bookingId) }
                .onSuccess { booking -> _uiState.update { it.copy(isCancelling = false, booking = booking) } }
                .onFailure { failure -> _uiState.update { it.copy(isCancelling = false, error = failure.toUiDataError()) } }
        }
    }

    private fun loadOptions(visitIndex: Int) = viewModelScope.launch(dispatcher) {
        runCatching { repository.workerOptions(bookingId, visitIndex) }
            .onSuccess { options -> _uiState.update {
                it.copy(activeVisitIndex = visitIndex, favourites = options.favourites, others = options.others, isLoading = false)
            } }
            .onFailure { failure -> _uiState.update { it.copy(isLoading = false, error = failure.toUiDataError()) } }
    }

    private fun poll() = viewModelScope.launch(dispatcher) {
        while (isActive) {
            val booking = runCatching { repository.booking(bookingId) }
                .onFailure { failure -> _uiState.update { it.copy(isLoading = false, error = failure.toUiDataError()) } }
                .getOrNull() ?: return@launch
            _uiState.update { it.copy(isLoading = false, booking = booking, remainingSeconds = countdown(booking), error = null) }
            if (booking.status == BookingStatus.AWAITING_WORKER && _uiState.value.favourites.isEmpty() && _uiState.value.others.isEmpty()) {
                loadOptions(booking.visits.firstOrNull { it.workerId == null }?.visitIndex ?: 0)
            }
            // Backward-compatible servers do not expose arrivalDueAt; avoid an endless legacy poll.
            if (booking.status == BookingStatus.CONFIRMED && booking.visits.none { it.arrivalDueAt != null }) return@launch
            if (booking.status in TERMINAL_STATUSES) return@launch
            delay(pollIntervalMillis)
        }
    }

    private fun countdown(booking: Booking): Long {
        val due = booking.visits.firstOrNull { it.jobStatus != "COMPLETED" }?.arrivalDueAt ?: return 0
        val server = booking.serverNow ?: return 0
        return runCatching { maxOf(0, Duration.between(Instant.parse(server), Instant.parse(due)).seconds) }.getOrDefault(0)
    }

    private companion object {
        val TERMINAL_STATUSES = setOf(BookingStatus.WORKER_UNAVAILABLE, BookingStatus.EXPIRED, BookingStatus.CANCELLED)
    }
}
