package com.snabbit.prototype.booking.controller

import com.snabbit.prototype.booking.domain.enums.BookingType
import com.snabbit.prototype.booking.config.BookingProperties
import com.snabbit.prototype.booking.dto.request.MatchAndHoldCommand
import com.snabbit.prototype.booking.dto.request.MatchAndHoldRequest
import com.snabbit.prototype.booking.dto.response.BookingAvailabilitySnapshot
import com.snabbit.prototype.booking.dto.response.BookingSnapshot
import com.snabbit.prototype.booking.exception.BookingRuleViolation
import com.snabbit.prototype.booking.matching.MatchAndHoldService
import jakarta.validation.Valid
import java.time.Duration
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/bookings")
class MatchAndHoldController(
    private val service: MatchAndHoldService,
    private val properties: BookingProperties,
) {
    @PostMapping("/availability")
    suspend fun availability(
        @RequestHeader("X-Authenticated-User-Id") authenticatedCustomerId: UUID,
        @Valid @RequestBody request: MatchAndHoldRequest,
    ): BookingAvailabilitySnapshot {
        requireOwner(authenticatedCustomerId, request.customerId)
        return service.availability(request.toCommand(properties))
    }

    @PostMapping("/match-and-hold")
    @ResponseStatus(HttpStatus.CREATED)
    suspend fun matchAndHold(
        @RequestHeader("X-Authenticated-User-Id") authenticatedCustomerId: UUID,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @Valid @RequestBody request: MatchAndHoldRequest,
    ): BookingSnapshot {
        requireOwner(authenticatedCustomerId, request.customerId)
        return service.matchAndHold(request.toCommand(properties, idempotencyKey))
    }
}

private fun MatchAndHoldRequest.toCommand(properties: BookingProperties, idempotencyKey: String? = null): MatchAndHoldCommand {
    if (startsAt.isNotEmpty() && visits.isNotEmpty()) {
        throw BookingRuleViolation("Supply either startsAt or visits, not both")
    }
    val resolvedStartsAt = if (visits.isNotEmpty()) {
        visits.map { visit -> visit.date.atTime(visit.startTime).atZone(properties.zoneId).toInstant() }
    } else {
        startsAt
    }
    val resolvedDurations = if (visits.isNotEmpty()) {
        visits.mapIndexed { index, visit ->
            val hours = visit.durationHours ?: durationHours
                ?: throw BookingRuleViolation("visits[$index].durationHours is required when durationHours is absent")
            Duration.ofHours(hours)
        }
    } else {
        val hours = durationHours ?: throw BookingRuleViolation("durationHours is required for the legacy startsAt request")
        resolvedStartsAt.map { Duration.ofHours(hours) }
    }
    val fallbackDuration = resolvedDurations.firstOrNull()
        ?: durationHours?.let(Duration::ofHours)
        ?: throw BookingRuleViolation("At least one visit duration is required")
    return MatchAndHoldCommand(
        customerId = customerId,
        serviceId = serviceId,
        bookingType = bookingType,
        duration = fallbackDuration,
        addressId = addressId,
        startsAt = resolvedStartsAt,
        visitDurations = resolvedDurations,
        expertPreference = expertPreference,
        favouriteWorkerId = favouriteWorkerId,
        fallbackPolicy = fallbackPolicy,
        workerTier = workerTier,
        idempotencyKey = idempotencyKey,
    )
}
