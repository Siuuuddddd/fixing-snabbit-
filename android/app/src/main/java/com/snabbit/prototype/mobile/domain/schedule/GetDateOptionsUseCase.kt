package com.snabbit.prototype.mobile.domain.schedule

import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

class GetDateOptionsUseCase(
    private val clock: Clock = Clock.system(ZoneId.of(BOOKING_ZONE)),
) {
    operator fun invoke(): ScheduleDateOptions {
        val today = LocalDate.now(clock.withZone(ZoneId.of(BOOKING_ZONE)))
        return ScheduleDateOptions(
            today = today,
            tomorrow = today.plusDays(1),
            multiDayChoices = (0L..6L).map(today::plusDays),
        )
    }

    private companion object {
        const val BOOKING_ZONE = "Asia/Kolkata"
    }
}
