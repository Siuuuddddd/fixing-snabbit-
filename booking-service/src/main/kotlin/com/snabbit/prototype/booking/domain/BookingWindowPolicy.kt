package com.snabbit.prototype.booking.domain

import com.snabbit.prototype.booking.config.BookingProperties
import com.snabbit.prototype.booking.domain.enums.BookingType
import com.snabbit.prototype.booking.exception.BookingRuleViolation
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import org.springframework.stereotype.Component

@Component
class BookingWindowPolicy(
    private val properties: BookingProperties,
    private val clock: Clock,
) {
    fun validate(type: BookingType, startsAt: Instant, duration: Duration) {
        if (startsAt.isBefore(clock.instant().plus(properties.minimumLeadTime))) {
            throw BookingRuleViolation("Visit start time does not meet the minimum lead time")
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
            throw BookingRuleViolation(
                "Visit must fit within operating hours ${properties.openingTime}-${properties.closingTime}",
            )
        }
        val lastBookableDate = today.plusDays((properties.advanceWindowDays - 1).toLong())
        if (localStart.toLocalDate().isBefore(today) || localStart.toLocalDate().isAfter(lastBookableDate)) {
            throw BookingRuleViolation(
                "Bookings are available only from today through $lastBookableDate (${properties.advanceWindowDays} calendar days)",
            )
        }
        if (type == BookingType.INSTANT && localStart.toLocalDate() != today) {
            throw BookingRuleViolation("Instant bookings must start today")
        }
    }
}
