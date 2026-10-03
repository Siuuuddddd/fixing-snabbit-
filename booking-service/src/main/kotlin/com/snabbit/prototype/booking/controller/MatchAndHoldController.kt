package com.snabbit.prototype.booking.controller

import com.snabbit.prototype.booking.dto.response.RatingContextSnapshot

import com.snabbit.prototype.booking.dto.response.PlusPassBookingContext

import com.snabbit.prototype.booking.dto.response.PaymentQuoteSnapshot

import com.snabbit.prototype.booking.dto.response.CancellationQuoteSnapshot

import com.snabbit.prototype.booking.dto.response.BookingSnapshot

import com.snabbit.prototype.booking.dto.response.BookingOwnershipSnapshot

import com.snabbit.prototype.booking.dto.response.BookingAvailabilitySnapshot

import com.snabbit.prototype.booking.dto.response.BookingPaymentSnapshot

import com.snabbit.prototype.booking.dto.request.VisitCommand

import com.snabbit.prototype.booking.dto.request.CreateBookingCommand

import com.snabbit.prototype.booking.dto.request.MatchAndHoldCommand

import com.snabbit.prototype.booking.dto.request.StartBookingPaymentCommand

import com.snabbit.prototype.booking.dto.request.MatchAndHoldRequest

import com.snabbit.prototype.booking.domain.enums.BookingType

import com.snabbit.prototype.booking.service.BookingLifecycleService
import com.snabbit.prototype.booking.service.BookingCancellationService
import com.snabbit.prototype.booking.domain.BookingRequirements
import com.snabbit.prototype.booking.matching.MatchAndHoldService
import com.snabbit.prototype.booking.payment.BookingPaymentService
import com.snabbit.prototype.booking.support.CustomerAccessDenied
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.Duration
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/v1/bookings")
class MatchAndHoldController(private val service: MatchAndHoldService) {
    @PostMapping("/availability")
    suspend fun availability(
        @RequestHeader("X-Authenticated-User-Id") authenticatedCustomerId: UUID,
        @Valid @RequestBody request: MatchAndHoldRequest,
    ): BookingAvailabilitySnapshot {
        requireOwner(authenticatedCustomerId, request.customerId)
        return service.availability(request.toCommand())
    }

    @PostMapping("/match-and-hold")
    @ResponseStatus(HttpStatus.CREATED)
    suspend fun matchAndHold(
        @RequestHeader("X-Authenticated-User-Id") authenticatedCustomerId: UUID,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @Valid @RequestBody request: MatchAndHoldRequest,
    ): BookingSnapshot {
        requireOwner(authenticatedCustomerId, request.customerId)
        return service.matchAndHold(request.toCommand(idempotencyKey))
    }
}

private fun MatchAndHoldRequest.toCommand(idempotencyKey: String? = null) = MatchAndHoldCommand(
    customerId = customerId,
    serviceId = serviceId,
    bookingType = bookingType,
    duration = Duration.ofHours(durationHours),
    addressId = addressId,
    startsAt = startsAt,
    idempotencyKey = idempotencyKey,
)
