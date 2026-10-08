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
import com.snabbit.prototype.mobile.domain.schedule.GetDateOptionsUseCase
import com.snabbit.prototype.mobile.domain.schedule.GetDurationsWithPriceUseCase
import com.snabbit.prototype.mobile.domain.schedule.ScheduleDateOption
import com.snabbit.prototype.mobile.domain.schedule.ValidateMultiDaySelectionUseCase
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
class ScheduleViewModelTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val clock = Clock.fixed(Instant.parse("2026-10-10T02:30:00Z"), zone)
    private val today = LocalDate.of(2026, 10, 10)

    @Test
    fun `date change clears duration start time and slots`() = runTest {
        val viewModel = viewModel(StandardTestDispatcher(testScheduler))
        runCurrent()
        viewModel.onEvent(ScheduleUiEvent.DateOptionSelected(ScheduleDateOption.TODAY))
        viewModel.onEvent(ScheduleUiEvent.DurationSelected(viewModel.uiState.value.durations[1]))
        runCurrent()
        viewModel.onEvent(ScheduleUiEvent.TimeSelected(viewModel.uiState.value.slots.first { it.available }.time))
        assertTrue(viewModel.uiState.value.canContinue)

        viewModel.onEvent(ScheduleUiEvent.DateOptionSelected(ScheduleDateOption.TOMORROW))

        assertNull(viewModel.uiState.value.selectedDuration)
        assertNull(viewModel.uiState.value.selectedTime)
        assertTrue(viewModel.uiState.value.slots.isEmpty())
        assertFalse(viewModel.uiState.value.canContinue)
    }

    @Test
    fun `duration change clears selected time and reloads slots`() = runTest {
        val viewModel = viewModel(StandardTestDispatcher(testScheduler))
        runCurrent()
        viewModel.onEvent(ScheduleUiEvent.DateOptionSelected(ScheduleDateOption.TOMORROW))
        viewModel.onEvent(ScheduleUiEvent.DurationSelected(viewModel.uiState.value.durations[0]))
        runCurrent()
        viewModel.onEvent(ScheduleUiEvent.TimeSelected(viewModel.uiState.value.slots.first().time))

        viewModel.onEvent(ScheduleUiEvent.DurationSelected(viewModel.uiState.value.durations[2]))

        assertNull(viewModel.uiState.value.selectedTime)
        runCurrent()
        assertTrue(viewModel.uiState.value.slots.isNotEmpty())
    }

    @Test
    fun `multi-day draft carries all free-choice dates with one time`() = runTest {
        val viewModel = viewModel(StandardTestDispatcher(testScheduler))
        runCurrent()
        viewModel.onEvent(ScheduleUiEvent.DateOptionSelected(ScheduleDateOption.MULTI_DAY))
        viewModel.onEvent(ScheduleUiEvent.MultiDayToggled(today))
        viewModel.onEvent(ScheduleUiEvent.MultiDayToggled(today.plusDays(3)))
        viewModel.onEvent(ScheduleUiEvent.DurationSelected(viewModel.uiState.value.durations[1]))
        runCurrent()
        viewModel.onEvent(ScheduleUiEvent.TimeSelected(viewModel.uiState.value.slots.first().time))

        val draft = requireNotNull(viewModel.draftOrNull())
        assertEquals(BookingType.MULTI_DAY, draft.bookingType)
        assertEquals(2, draft.durationHours)
        assertEquals(2, draft.startsAt.size)
    }

    @Test
    fun `continue stays disabled until a real available slot is selected`() = runTest {
        val viewModel = viewModel(StandardTestDispatcher(testScheduler))
        runCurrent()
        viewModel.onEvent(ScheduleUiEvent.DateOptionSelected(ScheduleDateOption.TODAY))
        viewModel.onEvent(ScheduleUiEvent.DurationSelected(viewModel.uiState.value.durations.first()))
        runCurrent()

        assertFalse(viewModel.uiState.value.canContinue)
        val soldOut = viewModel.uiState.value.slots.first { !it.available }
        viewModel.onEvent(ScheduleUiEvent.TimeSelected(soldOut.time))
        assertFalse(viewModel.uiState.value.canContinue)

        val available = viewModel.uiState.value.slots.first { it.available }
        viewModel.onEvent(ScheduleUiEvent.TimeSelected(available.time))
        assertTrue(viewModel.uiState.value.canContinue)
    }

    private fun viewModel(dispatcher: kotlinx.coroutines.CoroutineDispatcher) = ScheduleViewModel(
        serviceId = "service",
        addressId = "address",
        getDateOptions = GetDateOptionsUseCase(clock),
        getDurationsWithPrice = GetDurationsWithPriceUseCase(ScheduleCatalogRepository()),
        getAvailableSlots = GetAvailableSlotsUseCase(ScheduleBookingRepository(), clock),
        validateMultiDaySelection = ValidateMultiDaySelectionUseCase(clock),
        getHabitPackPromo = GetHabitPackPromoUseCase(SchedulePromoRepository()),
        dispatcher = dispatcher,
    )
}

private class ScheduleCatalogRepository : CatalogRepository {
    private val service = ServiceOffering("service", "HOME_CLEANING", "House Help", 13_500)
    override suspend fun services() = listOf(service)
    override suspend fun service(serviceId: String) = service.takeIf { it.id == serviceId }
}

private class SchedulePromoRepository : HabitPackPromoRepository {
    override suspend fun promo() = HabitPackPromo(3, 89_700, 27_900)
}

private class ScheduleBookingRepository : BookingRepository {
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
    override suspend fun startPayment(
        bookingId: String,
        walletId: String,
        useWallet: Boolean,
        idempotencyKey: String,
    ): BookingPayment = error("Not used")
}
