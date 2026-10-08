package com.snabbit.prototype.mobile.ui.booking

import com.snabbit.prototype.mobile.domain.booking.Booking
import com.snabbit.prototype.mobile.domain.booking.BookingAvailability
import com.snabbit.prototype.mobile.domain.booking.BookingDraft
import com.snabbit.prototype.mobile.domain.booking.BookingPayment
import com.snabbit.prototype.mobile.domain.booking.BookingRepository
import com.snabbit.prototype.mobile.domain.booking.BookingType
import com.snabbit.prototype.mobile.domain.booking.VisitAvailability
import com.snabbit.prototype.mobile.domain.catalog.CatalogRepository
import com.snabbit.prototype.mobile.domain.catalog.ServiceOffering
import com.snabbit.prototype.mobile.domain.home.GetHabitPackPromoUseCase
import com.snabbit.prototype.mobile.domain.home.HabitPackPromo
import com.snabbit.prototype.mobile.domain.home.HabitPackPromoRepository
import com.snabbit.prototype.mobile.domain.schedule.DayPart
import com.snabbit.prototype.mobile.domain.schedule.GetAvailableSlotsUseCase
import com.snabbit.prototype.mobile.domain.schedule.GetDurationsWithPriceUseCase
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleStartTimeViewModelTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val clock = Clock.fixed(Instant.parse("2026-10-10T02:30:00Z"), zone)
    private val today = LocalDate.of(2026, 10, 10)

    @Test
    fun `selecting an available time creates the expected draft`() = runTest {
        val viewModel = viewModel(StandardTestDispatcher(testScheduler), listOf(today, today.plusDays(2)))
        runCurrent()
        val available = viewModel.uiState.value.slots.first { it.available }
        viewModel.onEvent(ScheduleStartTimeEvent.TimeSelected(available.time))

        val draft = requireNotNull(viewModel.draftOrNull())
        assertEquals(BookingType.MULTI_DAY, draft.bookingType)
        assertEquals(2, draft.durationHours)
        assertEquals(2, draft.startsAt.size)
        assertTrue(viewModel.uiState.value.canContinue)
    }

    @Test
    fun `changing day part clears time and reloads slots`() = runTest {
        val viewModel = viewModel(StandardTestDispatcher(testScheduler), listOf(today.plusDays(1)))
        runCurrent()
        viewModel.onEvent(ScheduleStartTimeEvent.TimeSelected(viewModel.uiState.value.slots.first { it.available }.time))

        viewModel.onEvent(ScheduleStartTimeEvent.DayPartSelected(DayPart.AFTERNOON))

        assertNull(viewModel.uiState.value.selectedTime)
        assertFalse(viewModel.uiState.value.canContinue)
        runCurrent()
        assertTrue(viewModel.uiState.value.slots.isNotEmpty())
    }

    private fun viewModel(
        dispatcher: kotlinx.coroutines.CoroutineDispatcher,
        dates: List<LocalDate>,
    ) = ScheduleStartTimeViewModel(
        serviceId = "service",
        addressId = "address",
        dates = dates.map(LocalDate::toString),
        durationHours = 2,
        getDurationsWithPrice = GetDurationsWithPriceUseCase(TimeCatalogRepository()),
        getAvailableSlots = GetAvailableSlotsUseCase(TimeBookingRepository(), clock),
        getHabitPackPromo = GetHabitPackPromoUseCase(TimePromoRepository()),
        dispatcher = dispatcher,
    )
}

private class TimeCatalogRepository : CatalogRepository {
    private val service = ServiceOffering("service", "HOUSE_CLEANING", "House Help", 13_500)
    override suspend fun services() = listOf(service)
    override suspend fun service(serviceId: String) = service.takeIf { it.id == serviceId }
}

private class TimePromoRepository : HabitPackPromoRepository {
    override suspend fun promo() = HabitPackPromo(3, 89_700, 27_900)
}

private class TimeBookingRepository : BookingRepository {
    override suspend fun availability(draft: BookingDraft): BookingAvailability {
        val soldOut = draft.startsAt.first().contains("03:00:00Z")
        return BookingAvailability(
            durationMinutes = draft.durationHours * 60,
            allVisitsAvailable = !soldOut,
            sameWorkerAvailableForAllVisits = !soldOut,
            visits = draft.startsAt.map {
                VisitAvailability(it, !soldOut, if (soldOut) 0 else 2, if (soldOut) null else 13_500L * draft.durationHours)
            },
        )
    }

    override suspend fun matchAndHold(draft: BookingDraft, idempotencyKey: String): Booking = error("Not used")
    override suspend fun booking(bookingId: String): Booking = error("Not used")
    override suspend fun cancelHold(bookingId: String): Booking = error("Not used")
    override suspend fun startPayment(bookingId: String, walletId: String, useWallet: Boolean, idempotencyKey: String): BookingPayment = error("Not used")
}
