package com.snabbit.prototype.booking.dto.request

import com.snabbit.prototype.booking.domain.enums.BookingType
import java.time.Duration
import java.time.Instant
import java.util.UUID
import com.snabbit.prototype.booking.domain.enums.ExpertPreference
import com.snabbit.prototype.booking.domain.enums.FallbackPolicy
import com.snabbit.prototype.booking.domain.enums.WorkerTierSelection

data class MatchAndHoldCommand(
    val customerId: UUID,
    val serviceId: UUID,
    val bookingType: BookingType,
    val duration: Duration,
    val addressId: UUID,
    val startsAt: List<Instant>,
    val idempotencyKey: String? = null,
    val expertPreference: ExpertPreference = ExpertPreference.ANY,
    val favouriteWorkerId: UUID? = null,
    val fallbackPolicy: FallbackPolicy = FallbackPolicy.AUTO_ASSIGN,
    val visitDurations: List<Duration> = emptyList(),
    val workerTier: WorkerTierSelection? = null,
)
