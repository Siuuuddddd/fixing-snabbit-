package com.snabbit.prototype.booking.dto.request

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

import com.snabbit.prototype.booking.dto.request.VisitRequest

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

data class CreateBookingRequest(
    val customerId: UUID,
    val serviceId: UUID,
    val bookingType: BookingType,
    @field:Min(1) @field:Max(4)
    val durationHours: Long,
    @field:NotEmpty
    val visits: List<@Valid VisitRequest>,
    val requirements: BookingRequirements = BookingRequirements(),
)
