package com.snabbit.prototype.booking.domain

import com.snabbit.prototype.booking.config.BookingProperties
import com.snabbit.prototype.booking.support.BookingRuleViolation
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime

@Component
class BookingWindowPolicy(
    private val properties: BookingProperties,
    private val clock: Clock,
) {
    fun validate(type: BookingType, startsAt: Instant, duration: Duration) {
        if (startsAt.isBefore(clock.instant())) {
            throw BookingRuleViolation("Visit start time cannot be in the past")
        }
        val localStart = startsAt.atZone(properties.zoneId)
        val localEnd = localStart.plus(duration)
        val today = ZonedDateTime.now(clock.withZone(properties.zoneId)).toLocalDate()

        if (duration < Duration.ofHours(1) || duration > Duration.ofHours(4) || duration.toMinutes() % 60 != 0L) {
            throw BookingRuleViolation("Visit duration must be a whole number of hours from 1 to 4")
        }
        if (localStart.toLocalDate() != localEnd.toLocalDate() ||
            localStart.toLocalTime() < properties.openingTime ||
            localEnd.toLocalTime() > properties.closingTime
        ) {
            throw BookingRuleViolation("Visit must fit within operating hours 08:00-19:00")
        }
        if (localStart.toLocalDate().isBefore(today) || localStart.toLocalDate().isAfter(today.plusDays(7))) {
            throw BookingRuleViolation("Bookings are available only from today through the next seven days")
        }
        if (type == BookingType.INSTANT && localStart.toLocalDate() != today) {
            throw BookingRuleViolation("Instant bookings must start today")
        }
    }
}
