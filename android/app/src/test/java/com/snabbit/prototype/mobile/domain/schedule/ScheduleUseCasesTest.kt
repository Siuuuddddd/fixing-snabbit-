package com.snabbit.prototype.mobile.domain.schedule

import com.snabbit.prototype.mobile.domain.booking.Booking
import com.snabbit.prototype.mobile.domain.booking.BookingAvailability
import com.snabbit.prototype.mobile.domain.booking.BookingDraft
import com.snabbit.prototype.mobile.domain.booking.BookingPayment
import com.snabbit.prototype.mobile.domain.booking.BookingRepository
import com.snabbit.prototype.mobile.domain.booking.VisitAvailability
import com.snabbit.prototype.mobile.domain.catalog.CatalogRepository
import com.snabbit.prototype.mobile.domain.catalog.ServiceOffering
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleUseCasesTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val clock = Clock.fixed(Instant.parse("2026-10-10T02:30:00Z"), zone)
    private val today = LocalDate.of(2026, 10, 10)

    @Test
    fun `date options expose today tomorrow and seven bookable days`() {
        val options = GetDateOptionsUseCase(clock)()

        assertEquals(today, options.today)
        assertEquals(today.plusDays(1), options.tomorrow)
        assertEquals((0L..6L).map(today::plusDays), options.multiDayChoices)
    }

    @Test
    fun `multi-day rejects fewer than two days`() {
        val result = ValidateMultiDaySelectionUseCase(clock)(listOf(today))
        assertEquals(MultiDayValidationError.TOO_FEW_DAYS, result.error)
    }

    @Test
    fun `multi-day rejects more than seven days`() {
        val result = ValidateMultiDaySelectionUseCase(clock)((0L..7L).map(today::plusDays))
        assertEquals(MultiDayValidationError.TOO_MANY_DAYS, result.error)
    }

    @Test
    fun `multi-day accepts exactly two free-choice days`() {
        val result = ValidateMultiDaySelectionUseCase(clock)(listOf(today, today.plusDays(3)))
        assertTrue(result.isValid)
    }

    @Test
    fun `multi-day accepts exactly seven days`() {
        val result = ValidateMultiDaySelectionUseCase(clock)((0L..6L).map(today::plusDays))
        assertTrue(result.isValid)
    }

    @Test
    fun `multi-day rejects past dates`() {
        val result = ValidateMultiDaySelectionUseCase(clock)(listOf(today.minusDays(1), today.plusDays(1)))
        assertEquals(MultiDayValidationError.PAST_DATE, result.error)
    }

    @Test
    fun `durations use backend supported whole hours and catalogue prices`() = runTest {
        val durations = GetDurationsWithPriceUseCase(FakeCatalogRepository())("service")

        assertEquals(listOf(1, 2, 3, 4), durations.map { it.hours })
        assertEquals(listOf(13_500L, 27_000L, 40_500L, 54_000L), durations.map { it.price.currentPriceMinor })
        assertTrue(durations.all { it.price.originalPriceMinor == null })
    }

    @Test
    fun `today slots hide past times and preserve backend sold-out state`() = runTest {
        val useCase = GetAvailableSlotsUseCase(SlotAvailabilityRepository(), clock)

        val slots = useCase("service", "address", listOf(today), 1, DayPart.MORNING)

        assertEquals(LocalTime.of(8, 15), slots.first().time)
        assertFalse(slots.first { it.time == LocalTime.of(8, 30) }.available)
        assertEquals(13_500L, slots.first { it.available }.totalPriceMinor)
    }
}

private class FakeCatalogRepository : CatalogRepository {
    private val service = ServiceOffering("service", "HOME_CLEANING", "House Help", 13_500)
    override suspend fun services() = listOf(service)
    override suspend fun service(serviceId: String) = service.takeIf { it.id == serviceId }
}

private class SlotAvailabilityRepository : BookingRepository {
    override suspend fun availability(draft: BookingDraft): BookingAvailability {
        val startsAt = draft.startsAt.single()
        val available = !startsAt.contains("03:00:00Z") // 8:30 AM in Asia/Kolkata.
        return BookingAvailability(
            durationMinutes = draft.durationHours * 60,
            allVisitsAvailable = available,
            sameWorkerAvailableForAllVisits = available,
            visits = listOf(VisitAvailability(startsAt, available, if (available) 2 else 0, if (available) 13_500 else null)),
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
