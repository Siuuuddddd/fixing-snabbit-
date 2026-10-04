package com.snabbit.prototype.mobile.ui.tracking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snabbit.prototype.mobile.domain.booking.BookingRepository
import com.snabbit.prototype.mobile.domain.booking.BookingStatus
import com.snabbit.prototype.mobile.domain.tracking.TrackingRepository
import com.snabbit.prototype.mobile.domain.tracking.WorkerLocation
import com.snabbit.prototype.mobile.ui.common.UiDataError
import com.snabbit.prototype.mobile.ui.common.toUiDataError
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class TrackingConnection { CONNECTING, LIVE, RECONNECTING }

data class LiveTrackingUiState(
    val connection: TrackingConnection = TrackingConnection.CONNECTING,
    val location: WorkerLocation? = null,
    val replayedLatest: Boolean = false,
    val isStale: Boolean = false,
    val reconnectAttempt: Int = 0,
    val completedWorkerId: String? = null,
    val error: UiDataError? = null,
)

class LiveTrackingViewModel(
    private val bookingId: String,
    private val visitIndex: Int,
    private val trackingRepository: TrackingRepository,
    private val bookingRepository: BookingRepository,
    private val clock: Clock = Clock.systemUTC(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main,
    private val reconnectDelayMillis: Long = 2_000,
    private val statusPollMillis: Long = 5_000,
) : ViewModel() {
    private val _uiState = MutableStateFlow(LiveTrackingUiState())
    val uiState: StateFlow<LiveTrackingUiState> = _uiState.asStateFlow()
    private var streamJob: Job? = null

    init {
        connect()
        monitorVisit()
        monitorStaleness()
    }

    fun retry() = connect()

    private fun connect() {
        streamJob?.cancel()
        streamJob = viewModelScope.launch(dispatcher) {
            var attempt = 0
            while (currentCoroutineContext().isActive && _uiState.value.completedWorkerId == null) {
                _uiState.update {
                    it.copy(
                        connection = if (attempt == 0 && it.location == null) TrackingConnection.CONNECTING else TrackingConnection.RECONNECTING,
                        reconnectAttempt = attempt,
                        error = null,
                    )
                }
                runCatching { trackingRepository.latest(bookingId, visitIndex) }
                    .onSuccess { location -> applyLocation(location, replayed = attempt > 0) }
                    .onFailure { failure -> _uiState.update { it.copy(error = failure.toUiDataError()) } }
                try {
                    _uiState.update { it.copy(connection = TrackingConnection.LIVE) }
                    trackingRepository.stream(bookingId, visitIndex, _uiState.value.location?.locationId)
                        .collect { location -> applyLocation(location, replayed = false) }
                    attempt++
                } catch (failure: CancellationException) {
                    throw failure
                } catch (failure: Throwable) {
                    attempt++
                    _uiState.update {
                        it.copy(
                            connection = TrackingConnection.RECONNECTING,
                            reconnectAttempt = attempt,
                            error = failure.toUiDataError(),
                        )
                    }
                }
                delay((reconnectDelayMillis * attempt.coerceAtMost(5)).coerceAtLeast(reconnectDelayMillis))
            }
        }
    }

    private fun applyLocation(location: WorkerLocation, replayed: Boolean) = _uiState.update {
        if (it.location?.locationId == location.locationId) {
            it.copy(connection = TrackingConnection.LIVE, error = null)
        } else {
            it.copy(
                location = location,
                connection = TrackingConnection.LIVE,
                replayedLatest = replayed,
                isStale = location.isStale(clock),
                error = null,
            )
        }
    }

    private fun monitorVisit() = viewModelScope.launch(dispatcher) {
        while (currentCoroutineContext().isActive && _uiState.value.completedWorkerId == null) {
            runCatching { bookingRepository.booking(bookingId) }.getOrNull()?.let { booking ->
                val visit = booking.visits.singleOrNull { it.visitIndex == visitIndex }
                if (visit?.jobStatus == "COMPLETED" || booking.status == BookingStatus.COMPLETED) {
                    _uiState.update { it.copy(completedWorkerId = visit?.workerId) }
                    streamJob?.cancel()
                    return@launch
                }
            }
            delay(statusPollMillis)
        }
    }

    private fun monitorStaleness() = viewModelScope.launch(dispatcher) {
        while (currentCoroutineContext().isActive && _uiState.value.completedWorkerId == null) {
            delay(15_000)
            _uiState.update { state -> state.copy(isStale = state.location?.isStale(clock) == true) }
        }
    }

    private fun WorkerLocation.isStale(clock: Clock): Boolean = runCatching {
        Duration.between(Instant.parse(capturedAt), clock.instant()) > MAX_LOCATION_AGE
    }.getOrDefault(true)

    private companion object {
        val MAX_LOCATION_AGE: Duration = Duration.ofMinutes(2)
    }
}
