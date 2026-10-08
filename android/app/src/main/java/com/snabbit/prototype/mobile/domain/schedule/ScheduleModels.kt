package com.snabbit.prototype.mobile.domain.schedule

import java.time.LocalDate
import java.time.LocalTime

enum class ScheduleDateOption { TODAY, TOMORROW, MULTI_DAY }

enum class DayPart { MORNING, AFTERNOON, EVENING }

data class ScheduleDateOptions(
    val today: LocalDate,
    val tomorrow: LocalDate,
    val multiDayChoices: List<LocalDate>,
)

data class SchedulePrice(
    val currentPriceMinor: Long,
    val originalPriceMinor: Long? = null,
)

data class ServiceDuration(
    val hours: Int,
    val price: SchedulePrice,
)

data class TimeSlot(
    val time: LocalTime,
    val available: Boolean,
    val totalPriceMinor: Long?,
)

enum class MultiDayValidationError {
    TOO_FEW_DAYS,
    TOO_MANY_DAYS,
    PAST_DATE,
    OUTSIDE_BOOKING_WINDOW,
}

data class MultiDayValidation(
    val isValid: Boolean,
    val error: MultiDayValidationError? = null,
)
