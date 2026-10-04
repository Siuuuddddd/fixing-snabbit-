package com.snabbit.prototype.mobile.ui.cancellation

import com.snabbit.prototype.mobile.domain.booking.Booking
import com.snabbit.prototype.mobile.domain.booking.BookingStatus
import com.snabbit.prototype.mobile.domain.cancellation.CancellationPolicyBand
import com.snabbit.prototype.mobile.domain.cancellation.CancellationQuote
import com.snabbit.prototype.mobile.domain.cancellation.CancellationRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CancelBookingViewModelTest {
    @Test
    fun `loads authoritative breakdown and exposes backend cancellation result`() = runTest {
        val repository = CapturingCancellationRepository()
        val viewModel = CancelBookingViewModel("booking", repository, StandardTestDispatcher(testScheduler))
        runCurrent()

        assertEquals(2_000L, viewModel.uiState.value.quote?.cancellationFeeMinor)
        assertEquals(8_000L, viewModel.uiState.value.quote?.refundAmountMinor)
        viewModel.updateReason("Plans changed")
        viewModel.confirmCancellation()
        runCurrent()

        assertEquals("Plans changed", repository.reason)
        assertEquals(BookingStatus.CANCELLED, viewModel.uiState.value.cancelledBooking?.status)
        assertEquals(8_000L, viewModel.uiState.value.cancelledBooking?.refundAmountMinor)
    }
}

private class CapturingCancellationRepository : CancellationRepository {
    var reason: String? = null
    override suspend fun quote(bookingId: String) = CancellationQuote(
        bookingId, true, 2_000, 8_000, CancellationPolicyBand.LATE, "2026-10-04T10:00:00Z",
    )
    override suspend fun cancel(bookingId: String, reason: String?): Booking {
        this.reason = reason
        return Booking(bookingId, BookingStatus.CANCELLED, 10_000, 10_000, 0, emptyList(), "CUSTOMER", reason, 2_000, 8_000)
    }
}
