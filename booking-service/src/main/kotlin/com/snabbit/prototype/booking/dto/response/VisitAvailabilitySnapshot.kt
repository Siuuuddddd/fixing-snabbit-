package com.snabbit.prototype.booking.dto.response

import com.snabbit.prototype.booking.dto.response.BookingSnapshot

import com.snabbit.prototype.booking.dto.request.VisitCommand

import com.snabbit.prototype.booking.dto.request.CreateBookingCommand

import com.snabbit.prototype.booking.domain.enums.BookingType

import com.snabbit.prototype.booking.service.BookingLifecycleService
import com.snabbit.prototype.booking.catalog.ServiceCatalogService
import com.snabbit.prototype.booking.config.BookingProperties
import com.snabbit.prototype.booking.domain.BookingRequirements
import com.snabbit.prototype.booking.domain.BookingWindowPolicy
import com.snabbit.prototype.booking.support.BookingRuleViolation
import com.snabbit.prototype.booking.support.SlotUnavailable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.time.Clock
import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID

data class VisitAvailabilitySnapshot(
    val startsAt: Instant,
    val available: Boolean,
    val compatibleWorkerCount: Int,
    val lowestPriceMinor: Long?,
)
