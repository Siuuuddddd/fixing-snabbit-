package com.snabbit.prototype.mobile.ui.worker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snabbit.prototype.mobile.domain.address.DeviceLocationDataSource
import com.snabbit.prototype.mobile.domain.address.LocationFailure
import com.snabbit.prototype.mobile.domain.worker.*
import com.snabbit.prototype.mobile.ui.common.UiDataError
import com.snabbit.prototype.mobile.ui.common.toUiDataError
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class WorkerFeedUiState(
    val loading: Boolean = true, val refreshing: Boolean = false,
    val offers: List<WorkerOffer> = emptyList(), val jobs: List<WorkerJob> = emptyList(),
    val nowMillis: Long = 0, val changingOfferId: String? = null, val error: UiDataError? = null,
)

class WorkerJobFeedViewModel(
    private val repository: WorkerJobsRepository,
    private val clock: Clock = Clock.systemUTC(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main,
) : ViewModel() {
    private val _uiState = MutableStateFlow(WorkerFeedUiState(nowMillis = clock.millis()))
    val uiState = _uiState.asStateFlow()
    init {
        load(false)
        viewModelScope.launch(dispatcher) {
            while (isActive) { delay(1_000); _uiState.update { it.copy(nowMillis = clock.millis()) } }
        }
    }
    fun refresh() = load(true)
    fun retry() = load(false)
    fun accept(offer: WorkerOffer) = mutate(offer, true)
    fun reject(offer: WorkerOffer) = mutate(offer, false)
    private fun load(refreshing: Boolean) = viewModelScope.launch(dispatcher) {
        _uiState.update { it.copy(loading = !refreshing && it.offers.isEmpty() && it.jobs.isEmpty(), refreshing = refreshing, error = null) }
        runCatching { repository.offers() to repository.jobs() }
            .onSuccess { (offers, jobs) -> _uiState.update { it.copy(loading = false, refreshing = false, offers = offers, jobs = jobs) } }
            .onFailure { error -> _uiState.update { it.copy(loading = false, refreshing = false, error = error.toUiDataError()) } }
    }
    private fun mutate(offer: WorkerOffer, accepting: Boolean) = viewModelScope.launch(dispatcher) {
        _uiState.update { it.copy(changingOfferId = offer.offerId, error = null) }
        runCatching { if (accepting) repository.accept(offer.bookingId, offer.offerId) else repository.reject(offer.bookingId, offer.offerId) }
            .onSuccess { _uiState.update { it.copy(changingOfferId = null, offers = it.offers - offer) }; load(true) }
            .onFailure { error -> _uiState.update { it.copy(changingOfferId = null, error = error.toUiDataError()) } }
    }
}

data class WorkerScheduleUiState(
    val loading: Boolean = true, val days: List<WorkerAvailability> = emptyList(),
    val changingDate: String? = null, val error: UiDataError? = null,
)
class WorkerScheduleViewModel(
    private val repository: WorkerAvailabilityRepository,
    private val clock: Clock = Clock.systemUTC(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main,
) : ViewModel() {
    private val _uiState = MutableStateFlow(WorkerScheduleUiState())
    val uiState = _uiState.asStateFlow()
    init { load() }
    fun retry() = load()
    fun toggle(date: String, currently: WorkerAvailabilityStatus?) = viewModelScope.launch(dispatcher) {
        val next = if (currently == WorkerAvailabilityStatus.AVAILABLE) WorkerAvailabilityStatus.UNAVAILABLE else WorkerAvailabilityStatus.AVAILABLE
        _uiState.update { it.copy(changingDate = date, error = null) }
        runCatching { repository.update(date, next) }
            .onSuccess { updated -> _uiState.update { state -> state.copy(changingDate = null, days = mergeDays(state.days, updated)) } }
            .onFailure { error -> _uiState.update { it.copy(changingDate = null, error = error.toUiDataError()) } }
    }
    private fun load() = viewModelScope.launch(dispatcher) {
        _uiState.update { it.copy(loading = true, error = null) }
        val today = LocalDate.now(clock)
        runCatching { repository.availability(today.toString(), today.plusDays(6).toString()) }
            .onSuccess { saved ->
                val byDate = saved.associateBy { it.serviceDate }
                val days = (0L..6L).map { offset -> byDate[today.plusDays(offset).toString()] ?: WorkerAvailability(today.plusDays(offset).toString(), "08:00:00", "19:00:00", WorkerAvailabilityStatus.UNAVAILABLE) }
                _uiState.update { it.copy(loading = false, days = days) }
            }.onFailure { error -> _uiState.update { it.copy(loading = false, error = error.toUiDataError()) } }
    }
    private fun mergeDays(days: List<WorkerAvailability>, updated: WorkerAvailability) = days.map { if (it.serviceDate == updated.serviceDate) updated else it }
}

data class WorkerJobDetailsUiState(
    val loading: Boolean = true, val job: WorkerJob? = null, val changing: Boolean = false,
    val publishing: Boolean = false, val locationPermissionNeeded: Boolean = false,
    val locationError: Boolean = false, val locationPublished: Boolean = false, val error: UiDataError? = null,
)
class WorkerJobDetailsViewModel(
    private val bookingId: String, private val visitIndex: Int,
    private val jobs: WorkerJobsRepository, private val locations: WorkerLocationRepository,
    private val deviceLocation: DeviceLocationDataSource,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main,
) : ViewModel() {
    private val _uiState = MutableStateFlow(WorkerJobDetailsUiState())
    val uiState = _uiState.asStateFlow()
    init { load() }
    fun retry() = load()
    fun requestArrival() = capture(andArrive = true)
    fun publishLocation() = capture(andArrive = false)
    fun locationPermissionDenied() = _uiState.update { it.copy(locationPermissionNeeded = false, locationError = true) }
    fun start() = transition(WorkerJobStatus.IN_PROGRESS) { jobs.start(bookingId, visitIndex) }
    fun complete() = transition(WorkerJobStatus.COMPLETED) { jobs.complete(bookingId, visitIndex) }
    private fun capture(andArrive: Boolean) = viewModelScope.launch(dispatcher) {
        _uiState.update { it.copy(publishing = true, locationPermissionNeeded = false, locationError = false, error = null) }
        runCatching {
            val point = deviceLocation.currentLocation()
            locations.publish(point)
            if (andArrive) jobs.arrive(bookingId, visitIndex, point)
        }.onSuccess {
            _uiState.update { state -> state.copy(
                publishing = false,
                locationPublished = true,
                job = if (andArrive) state.job?.copy(status = WorkerJobStatus.ARRIVED) else state.job,
            ) }
        }
            .onFailure { error ->
                if (error is LocationFailure) _uiState.update { it.copy(publishing = false, locationPermissionNeeded = true, locationError = true) }
                else _uiState.update { it.copy(publishing = false, error = error.toUiDataError()) }
            }
    }
    private fun transition(status: WorkerJobStatus, block: suspend () -> Unit) = viewModelScope.launch(dispatcher) {
        _uiState.update { it.copy(changing = true, error = null) }
        runCatching { block() }
            .onSuccess { _uiState.update { it.copy(changing = false, job = it.job?.copy(status = status)) } }
            .onFailure { error -> _uiState.update { it.copy(changing = false, error = error.toUiDataError()) } }
    }
    private fun load() = viewModelScope.launch(dispatcher) {
        _uiState.update { it.copy(loading = it.job == null, changing = false, error = null) }
        runCatching { jobs.jobs().firstOrNull { it.bookingId == bookingId && it.visitIndex == visitIndex } }
            .onSuccess { job -> _uiState.update { it.copy(loading = false, job = job) } }
            .onFailure { error -> _uiState.update { it.copy(loading = false, error = error.toUiDataError()) } }
    }
}

data class WorkerEarningsUiState(val loading: Boolean = false, val error: UiDataError? = null)
class WorkerEarningsViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(WorkerEarningsUiState())
    val uiState = _uiState.asStateFlow()
    fun retry() = Unit
}
