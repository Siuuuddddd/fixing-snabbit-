package com.snabbit.prototype.mobile.ui.completed

import com.snabbit.prototype.mobile.domain.booking.Booking
import com.snabbit.prototype.mobile.domain.booking.BookingAvailability
import com.snabbit.prototype.mobile.domain.booking.BookingDraft
import com.snabbit.prototype.mobile.domain.booking.BookingPayment
import com.snabbit.prototype.mobile.domain.booking.BookingRepository
import com.snabbit.prototype.mobile.domain.booking.BookingStatus
import com.snabbit.prototype.mobile.domain.rating.RatingRepository
import com.snabbit.prototype.mobile.domain.rating.WorkerRating
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BookingCompletedViewModelTest {
    @Test
    fun `requires stars then submits exact completed visit rating`() = runTest {
        val ratings = CapturingRatingRepository()
        val viewModel = BookingCompletedViewModel(
            "booking", 2, "worker", CompletedBookingRepository(), ratings,
            StandardTestDispatcher(testScheduler),
        )
        runCurrent()
        viewModel.submitRating()
        assertTrue(viewModel.uiState.value.validationError)

        viewModel.selectStars(5)
        viewModel.updateComment("Excellent")
        viewModel.submitRating()
        runCurrent()

        assertEquals("booking", ratings.bookingId)
        assertEquals(2, ratings.visitIndex)
        assertEquals(5, ratings.stars)
        assertEquals("rating", viewModel.uiState.value.rating?.ratingId)
    }
}

private class CapturingRatingRepository : RatingRepository {
    var bookingId: String? = null
    var visitIndex: Int? = null
    var stars: Int? = null
    override suspend fun rateWorker(workerId: String, bookingId: String, visitIndex: Int, stars: Int, comment: String?): WorkerRating {
        this.bookingId = bookingId
        this.visitIndex = visitIndex
        this.stars = stars
        return WorkerRating("rating", bookingId, visitIndex, workerId, stars, comment, "4.8")
    }
}

private class CompletedBookingRepository : BookingRepository {
    override suspend fun booking(bookingId: String) = Booking(bookingId, BookingStatus.COMPLETED, 10_000, 10_000, 0, emptyList())
    override suspend fun availability(draft: BookingDraft): BookingAvailability = error("not used")
    override suspend fun matchAndHold(draft: BookingDraft, idempotencyKey: String): Booking = error("not used")
    override suspend fun cancelHold(bookingId: String): Booking = error("not used")
    override suspend fun startPayment(bookingId: String, walletId: String, useWallet: Boolean, idempotencyKey: String): BookingPayment = error("not used")
}
