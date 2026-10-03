package com.snabbit.prototype.booking.dto.response

import com.snabbit.prototype.booking.domain.enums.WorkerOfferStatus

import com.snabbit.prototype.booking.domain.enums.SlotHoldStatus

import com.snabbit.prototype.booking.domain.enums.JobStatus

import com.snabbit.prototype.booking.catalog.ServiceOfferingRepository
import com.snabbit.prototype.booking.domain.BookingRequirements
import com.snabbit.prototype.booking.repository.BookingRepository
import com.snabbit.prototype.booking.repository.SlotHoldRepository
import com.snabbit.prototype.booking.repository.WorkerOfferRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

data class WorkerOfferFeedItem(
    val offerId: UUID,
    val bookingId: UUID,
    val visitIndex: Int,
    val serviceId: UUID,
    val serviceCode: String,
    val serviceName: String,
    val startsAt: Instant,
    val endsAt: Instant,
    val durationMinutes: Int,
    val offerExpiresAt: Instant,
    val requirements: BookingRequirements,
)
