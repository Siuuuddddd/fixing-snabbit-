package com.snabbit.prototype.mobile.domain.schedule

import com.snabbit.prototype.mobile.domain.booking.BookingDraft
import com.snabbit.prototype.mobile.domain.booking.BookingRepository
import com.snabbit.prototype.mobile.domain.booking.BookingType
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class GetAvailableSlotsUseCase(
    private val bookingRepository: BookingRepository,
    private val clock: Clock = Clock.system(ZoneId.of(BOOKING_ZONE)),
) {
    suspend operator fun invoke(
        serviceId: String,
        addressId: String,
        dates: List<LocalDate>,
        durationHours: Int,
        dayPart: DayPart,
    ): List<TimeSlot> {
        if (dates.isEmpty() || durationHours !in 1..4) return emptyList()
        val zone = ZoneId.of(BOOKING_ZONE)
        val now = clock.instant().atZone(zone)
        val bookingType = if (dates.size > 1) BookingType.MULTI_DAY else BookingType.SCHEDULED
        val candidates = candidateTimes(dayPart, durationHours)
            .filter { time -> dates.none { date -> date == now.toLocalDate() && !date.atTime(time).atZone(zone).isAfter(now) } }
        return coroutineScope {
            val permits = Semaphore(MAX_CONCURRENT_REQUESTS)
            candidates.map { time ->
                async {
                    permits.withPermit {
                        val draft = BookingDraft(
                            serviceId = serviceId,
                            addressId = addressId,
                            bookingType = bookingType,
                            durationHours = durationHours,
                            startsAt = dates.sorted().map { date -> date.atTime(time).atZone(zone).toInstant().toString() },
                        )
                        val availability = bookingRepository.availability(draft)
                        TimeSlot(
                            time = time,
                            available = availability.allVisitsAvailable,
                            totalPriceMinor = availability.visits
                                .mapNotNull { it.lowestPriceMinor }
                                .takeIf { it.size == availability.visits.size }
                                ?.sum(),
                        )
                    }
                }
            }.awaitAll()
        }
    }

    private fun candidateTimes(dayPart: DayPart, durationHours: Int): List<LocalTime> {
        val latestStart = LocalTime.of(CLOSING_HOUR - durationHours, 0)
        val start = when (dayPart) {
            DayPart.MORNING -> LocalTime.of(OPENING_HOUR, 0)
            DayPart.AFTERNOON -> LocalTime.NOON
            DayPart.EVENING -> LocalTime.of(EVENING_START_HOUR, 0)
        }
        val partEnd = when (dayPart) {
            DayPart.MORNING -> LocalTime.NOON.minusMinutes(15)
            DayPart.AFTERNOON -> LocalTime.of(EVENING_START_HOUR, 0).minusMinutes(15)
            DayPart.EVENING -> latestStart
        }
        val end = minOf(partEnd, latestStart)
        if (start > end) return emptyList()
        return generateSequence(start) { it.plusMinutes(15) }
            .takeWhile { it <= end }
            .toList()
    }

    private companion object {
        const val BOOKING_ZONE = "Asia/Kolkata"
        const val OPENING_HOUR = 8
        const val EVENING_START_HOUR = 16
        const val CLOSING_HOUR = 19
        const val MAX_CONCURRENT_REQUESTS = 4
    }
}
