package com.snabbit.prototype.mobile.ui.bookings

import com.snabbit.prototype.mobile.domain.booking.Booking
import com.snabbit.prototype.mobile.domain.booking.BookingStatus
import com.snabbit.prototype.mobile.domain.bookings.BookingListRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MyBookingsViewModelTest {
    @Test fun `separates backend bookings into upcoming and past`() = runTest {
        val repository = BookingListRepository {
            listOf(booking("upcoming", BookingStatus.CONFIRMED), booking("past", BookingStatus.COMPLETED))
        }
        val viewModel = MyBookingsViewModel(repository, StandardTestDispatcher(testScheduler))
        runCurrent()
        assertEquals(listOf("upcoming"), viewModel.uiState.value.visibleBookings.map { it.id })
        viewModel.selectFilter(BookingListFilter.PAST)
        assertEquals(listOf("past"), viewModel.uiState.value.visibleBookings.map { it.id })
    }
}

private fun booking(id: String, status: BookingStatus) = Booking(id, status, 10_000, 10_000, 0, emptyList())
