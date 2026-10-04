package com.snabbit.prototype.mobile.ui.tracking

import com.snabbit.prototype.mobile.domain.booking.Booking
import com.snabbit.prototype.mobile.domain.booking.BookingAvailability
import com.snabbit.prototype.mobile.domain.booking.BookingDraft
import com.snabbit.prototype.mobile.domain.booking.BookingPayment
import com.snabbit.prototype.mobile.domain.booking.BookingRepository
import com.snabbit.prototype.mobile.domain.booking.BookingStatus
import com.snabbit.prototype.mobile.domain.booking.BookingVisit
import com.snabbit.prototype.mobile.domain.common.DataFailure
import com.snabbit.prototype.mobile.domain.common.DataFailureReason
import com.snabbit.prototype.mobile.domain.tracking.TrackingRepository
import com.snabbit.prototype.mobile.domain.tracking.WorkerLocation
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LiveTrackingViewModelTest {
    @Test
    fun `reconnect fetches latest replay and sends last event id`() = runTest {
        val tracking = ReconnectingTrackingRepository()
        val bookings = CompletingBookingRepository()
        val viewModel = LiveTrackingViewModel(
            "booking", 0, tracking, bookings,
            Clock.fixed(Instant.parse("2026-10-04T10:01:00Z"), ZoneOffset.UTC),
            StandardTestDispatcher(testScheduler), reconnectDelayMillis = 100, statusPollMillis = 500,
        )
        runCurrent()
        advanceTimeBy(100)
        runCurrent()

        assertEquals(2, tracking.latestCalls)
        assertEquals(listOf("location-1", "location-2"), tracking.lastEventIds)
        assertTrue(viewModel.uiState.value.replayedLatest)
        assertEquals("location-2", viewModel.uiState.value.location?.locationId)

        advanceTimeBy(400)
        runCurrent()
        assertEquals("worker", viewModel.uiState.value.completedWorkerId)
    }
}

private class ReconnectingTrackingRepository : TrackingRepository {
    var latestCalls = 0
    val lastEventIds = mutableListOf<String?>()
    override suspend fun latest(bookingId: String, visitIndex: Int): WorkerLocation {
        latestCalls++
        return location("location-$latestCalls")
    }
    override fun stream(bookingId: String, visitIndex: Int, lastEventId: String?): Flow<WorkerLocation> = flow {
        lastEventIds += lastEventId
        if (lastEventIds.size == 1) throw DataFailure(DataFailureReason.OFFLINE)
        awaitCancellation()
    }
}

private class CompletingBookingRepository : BookingRepository {
    private var calls = 0
    override suspend fun booking(bookingId: String): Booking {
        calls++
        val complete = calls > 1
        return Booking(
            "booking", if (complete) BookingStatus.COMPLETED else BookingStatus.CONFIRMED,
            10_000, 10_000, 0,
            listOf(BookingVisit(0, "worker", "2026-10-04T10:00:00Z", "2026-10-04T12:00:00Z", "ACCEPTED", if (complete) "COMPLETED" else "IN_PROGRESS")),
        )
    }
    override suspend fun availability(draft: BookingDraft): BookingAvailability = error("not used")
    override suspend fun matchAndHold(draft: BookingDraft, idempotencyKey: String): Booking = error("not used")
    override suspend fun cancelHold(bookingId: String): Booking = error("not used")
    override suspend fun startPayment(bookingId: String, walletId: String, useWallet: Boolean, idempotencyKey: String): BookingPayment = error("not used")
}

private fun location(id: String) = WorkerLocation(
    id, "booking", 0, "worker", 19.1, 72.9, 12.0,
    "2026-10-04T10:00:00Z", "2026-10-04T10:00:01Z",
)
