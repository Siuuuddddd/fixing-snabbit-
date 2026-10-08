package com.snabbit.prototype.mobile.domain.schedule

import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

class ValidateMultiDaySelectionUseCase(
    private val clock: Clock = Clock.system(ZoneId.of(BOOKING_ZONE)),
) {
    operator fun invoke(dates: Collection<LocalDate>): MultiDayValidation {
        val distinct = dates.toSet()
        val today = LocalDate.now(clock.withZone(ZoneId.of(BOOKING_ZONE)))
        return when {
            distinct.size > 7 -> MultiDayValidation(false, MultiDayValidationError.TOO_MANY_DAYS)
            distinct.any { it.isBefore(today) } -> MultiDayValidation(false, MultiDayValidationError.PAST_DATE)
            distinct.any { it.isAfter(today.plusDays(6)) } ->
                MultiDayValidation(false, MultiDayValidationError.OUTSIDE_BOOKING_WINDOW)
            distinct.size < 2 -> MultiDayValidation(false, MultiDayValidationError.TOO_FEW_DAYS)
            else -> MultiDayValidation(true)
        }
    }

    private companion object {
        const val BOOKING_ZONE = "Asia/Kolkata"
    }
}
