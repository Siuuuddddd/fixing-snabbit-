package com.snabbit.prototype.booking.domain

import com.snabbit.prototype.booking.domain.enums.BookingType

import com.snabbit.prototype.booking.config.BookingProperties
import com.snabbit.prototype.booking.support.BookingRuleViolation
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

class BookingWindowPolicyTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val clock = Clock.fixed(Instant.parse("2026-10-02T04:00:00Z"), ZoneOffset.UTC)
    private val policy = BookingWindowPolicy(
        BookingProperties(
            zone,
            LocalTime.of(8, 0),
            LocalTime.of(19, 0),
            Duration.ofMinutes(5),
            Duration.ofMinutes(1),
            Duration.ofSeconds(30),
            100,
        ),
        clock,
    )

    @Test
    fun `accepts a scheduled visit within the next seven days`() {
        val start = Instant.parse("2026-10-05T04:30:00Z") // 10:00 Asia/Kolkata

        assertDoesNotThrow { policy.validate(BookingType.SCHEDULED, start, Duration.ofHours(2)) }
    }

    @Test
    fun `rejects instant visits after today`() {
        val tomorrow = Instant.parse("2026-10-03T04:30:00Z")

        assertThrows(BookingRuleViolation::class.java) {
            policy.validate(BookingType.INSTANT, tomorrow, Duration.ofHours(1))
        }
    }

    @Test
    fun `rejects a visit ending after operating hours`() {
        val lateStart = Instant.parse("2026-10-02T12:30:00Z") // 18:00 Asia/Kolkata

        assertThrows(BookingRuleViolation::class.java) {
            policy.validate(BookingType.SCHEDULED, lateStart, Duration.ofHours(2))
        }
    }

    @Test
    fun `rejects fractional and overlong durations`() {
        val start = Instant.parse("2026-10-02T04:30:00Z")

        assertThrows(BookingRuleViolation::class.java) {
            policy.validate(BookingType.SCHEDULED, start, Duration.ofMinutes(90))
        }
        assertThrows(BookingRuleViolation::class.java) {
            policy.validate(BookingType.SCHEDULED, start, Duration.ofHours(5))
        }
    }
}
