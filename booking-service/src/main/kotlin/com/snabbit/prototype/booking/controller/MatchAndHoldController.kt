package com.snabbit.prototype.booking.controller

import com.snabbit.prototype.booking.domain.enums.BookingType
import com.snabbit.prototype.booking.config.BookingProperties
import com.snabbit.prototype.booking.dto.request.MatchAndHoldCommand
import com.snabbit.prototype.booking.dto.request.MatchAndHoldRequest
import com.snabbit.prototype.booking.dto.response.BookingAvailabilitySnapshot
import com.snabbit.prototype.booking.dto.response.BookingSnapshot
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

private fun MatchAndHoldRequest.toCommand(properties: BookingProperties, idempotencyKey: String? = null) = MatchAndHoldCommand(
    customerId = customerId,
    serviceId = serviceId,
    bookingType = bookingType,
    duration = Duration.ofHours(durationHours),
    addressId = addressId,
    startsAt = when {
        startsAt.isNotEmpty() && visits.isNotEmpty() -> throw com.snabbit.prototype.booking.exception.BookingRuleViolation(
            "Supply either startsAt or visits, not both",
        )
        visits.isNotEmpty() -> visits.map { visit ->
            visit.date.atTime(visit.startTime).atZone(properties.zoneId).toInstant()
        }
        else -> startsAt
    },
    expertPreference = expertPreference,
    favouriteWorkerId = favouriteWorkerId,
    fallbackPolicy = fallbackPolicy,
    idempotencyKey = idempotencyKey,
)
