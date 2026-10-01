package com.snabbit.prototype.booking.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId

@ConfigurationProperties("booking")
data class BookingProperties(
    val zoneId: ZoneId,
    val openingTime: LocalTime,
    val closingTime: LocalTime,
    val slotHoldDuration: Duration,
    val workerOfferDuration: Duration,
)
