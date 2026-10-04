package com.snabbit.prototype.mobile.ui.worker

import androidx.lifecycle.viewModelScope
import com.snabbit.prototype.mobile.domain.address.*
import com.snabbit.prototype.mobile.domain.worker.*
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WorkerViewModelsTest {
    private val dispatcher get() = StandardTestDispatcher()

    @Test fun `feed loads offers and accepts using booking and offer ids`() = runTest {
        val repository = FakeWorkerJobsRepository(offers = mutableListOf(offer()))
        val viewModel = WorkerJobFeedViewModel(repository, Clock.fixed(Instant.parse("2026-10-04T10:00:00Z"), ZoneOffset.UTC), StandardTestDispatcher(testScheduler))
        runCurrent()
        assertEquals("offer-1", viewModel.uiState.value.offers.single().offerId)
        viewModel.accept(viewModel.uiState.value.offers.single())
        runCurrent()
        assertEquals("booking-1" to "offer-1", repository.accepted)
        viewModel.viewModelScope.cancel()
    }

    @Test fun `offer countdown reaches zero and never becomes negative`() {
        val now = Instant.parse("2026-10-04T10:00:00Z").toEpochMilli()
        assertEquals(90, offerSecondsRemaining(now, "2026-10-04T10:01:30Z"))
        assertEquals(0, offerSecondsRemaining(now, "2026-10-04T09:59:00Z"))
    }

    @Test fun `schedule fills unsaved days and updates one explicitly`() = runTest {
        val repository = FakeAvailabilityRepository()
        val viewModel = WorkerScheduleViewModel(repository, Clock.fixed(Instant.parse("2026-10-04T10:00:00Z"), ZoneOffset.UTC), StandardTestDispatcher(testScheduler))
        runCurrent()
        assertEquals(7, viewModel.uiState.value.days.size)
        val first = viewModel.uiState.value.days.first()
        viewModel.toggle(first.serviceDate, first.status)
        runCurrent()
        assertEquals(WorkerAvailabilityStatus.AVAILABLE, viewModel.uiState.value.days.first().status)
    }

    @Test fun `arrival publishes fresh location before lifecycle transition`() = runTest {
        val repository = FakeWorkerJobsRepository(jobs = mutableListOf(job()))
        val events = mutableListOf<String>()
        val location = LocationSample(19.0, 72.8, 12.0, "2026-10-04T10:00:00Z")
        val viewModel = WorkerJobDetailsViewModel("booking-1", 2, repository,
            object : WorkerLocationRepository { override suspend fun publish(location: LocationSample) { events += "publish" } },
            object : DeviceLocationDataSource { override suspend fun currentLocation() = location },
            StandardTestDispatcher(testScheduler))
        repository.arrivalEvents = events
        runCurrent()
        viewModel.requestArrival()
        runCurrent()
        assertEquals(listOf("publish", "arrive"), events)
        assertTrue(viewModel.uiState.value.locationPublished)
    }
}

private val noRequirements = WorkerRequirements(
    false, null, null, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(),
    false, emptyList(), emptyList(),
)
private fun offer() = WorkerOffer("offer-1", "booking-1", 2, "Home cleaning", "2026-10-04T11:00:00Z", "2026-10-04T12:00:00Z", 60, "2026-10-04T10:05:00Z", noRequirements)
private fun job() = WorkerJob("booking-1", 2, "Home cleaning", "2026-10-04T11:00:00Z", "2026-10-04T12:00:00Z", 60, WorkerJobStatus.ASSIGNED, noRequirements, "Home", "Bandra", "400050")

private class FakeWorkerJobsRepository(
    private val offers: MutableList<WorkerOffer> = mutableListOf(),
    private val jobs: MutableList<WorkerJob> = mutableListOf(),
) : WorkerJobsRepository {
    var accepted: Pair<String, String>? = null
    var arrivalEvents: MutableList<String>? = null
    override suspend fun offers() = offers.toList()
    override suspend fun jobs() = jobs.toList()
    override suspend fun accept(bookingId: String, offerId: String) { accepted = bookingId to offerId; offers.clear() }
    override suspend fun reject(bookingId: String, offerId: String) { offers.clear() }
    override suspend fun arrive(bookingId: String, visitIndex: Int, location: LocationSample) { arrivalEvents?.add("arrive") }
    override suspend fun start(bookingId: String, visitIndex: Int) = Unit
    override suspend fun complete(bookingId: String, visitIndex: Int) = Unit
}

private class FakeAvailabilityRepository : WorkerAvailabilityRepository {
    override suspend fun availability(from: String, to: String) = emptyList<WorkerAvailability>()
    override suspend fun update(date: String, status: WorkerAvailabilityStatus) = WorkerAvailability(date, "08:00:00", "19:00:00", status)
}
