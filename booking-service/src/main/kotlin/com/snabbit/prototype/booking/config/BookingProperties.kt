package com.snabbit.prototype.booking.config

import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("booking")
data class BookingProperties(
    val zoneId: ZoneId,
    val openingTime: LocalTime,
    val closingTime: LocalTime,
    val slotHoldDuration: Duration,
    val workerOfferDuration: Duration,
    val expiryScanDelay: Duration,
    val expiryBatchSize: Int,
    val advanceWindowDays: Int = 10,
    val minMultiDayVisits: Int = 2,
    val maxMultiDayVisits: Int = advanceWindowDays,
    val minimumLeadTime: Duration = Duration.ZERO,
    val paymentHoldExtension: Duration = Duration.ofMinutes(10),
    val maximumTotalHoldTime: Duration = Duration.ofMinutes(20),
    val plusVeteranUpgradeFree: Boolean = true,
    val arrivalAccuracyMeters: Double = 100.0,
    val arrivalRadiusMeters: Double = 200.0,
) {
    init {
        require(advanceWindowDays > 0) { "booking.advance-window-days must be positive" }
        require(minMultiDayVisits >= 2) { "booking.min-multi-day-visits must be at least 2" }
        require(maxMultiDayVisits in minMultiDayVisits..advanceWindowDays) {
            "booking.max-multi-day-visits must be between the minimum and advance window"
        }
        require(!minimumLeadTime.isNegative) { "booking.minimum-lead-time cannot be negative" }
        require(paymentHoldExtension > Duration.ZERO) { "booking.payment-hold-extension must be positive" }
        require(maximumTotalHoldTime >= slotHoldDuration) {
            "booking.maximum-total-hold-time must cover the initial slot hold"
        }
    }
}
