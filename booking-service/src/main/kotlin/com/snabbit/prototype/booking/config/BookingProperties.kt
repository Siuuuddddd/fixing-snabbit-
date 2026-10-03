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
    val arrivalAccuracyMeters: Double = 100.0,
    val arrivalRadiusMeters: Double = 200.0,
)
