package com.snabbit.prototype.booking.controller

import com.snabbit.prototype.booking.service.BookingLifecycleService
import com.snabbit.prototype.booking.service.BookingSnapshot
import com.snabbit.prototype.booking.service.PaymentQuoteSnapshot
import com.snabbit.prototype.booking.service.RatingContextSnapshot
import com.snabbit.prototype.booking.service.BookingOwnershipSnapshot
import com.snabbit.prototype.booking.service.PlusPassBookingContext
import com.snabbit.prototype.booking.service.BookingCancellationService
import com.snabbit.prototype.booking.service.CancellationQuoteSnapshot
import com.snabbit.prototype.booking.service.CreateBookingCommand
import com.snabbit.prototype.booking.service.VisitCommand
import com.snabbit.prototype.booking.domain.BookingType
import com.snabbit.prototype.booking.domain.BookingRequirements
import com.snabbit.prototype.booking.matching.MatchAndHoldCommand
import com.snabbit.prototype.booking.matching.MatchAndHoldService
import com.snabbit.prototype.booking.matching.BookingAvailabilitySnapshot
import com.snabbit.prototype.booking.payment.BookingPaymentSnapshot
import com.snabbit.prototype.booking.payment.BookingPaymentService
import com.snabbit.prototype.booking.payment.StartBookingPaymentCommand
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

data class VisitRequest(
    val workerId: UUID,
    val startsAt: Instant,
)

data class MatchAndHoldRequest(
    val customerId: UUID,
    val serviceId: UUID,
    val bookingType: BookingType,
    @field:Min(1) @field:Max(4)
    val durationHours: Long,
    val addressId: UUID,
    @field:NotEmpty @field:Size(max = 7)
    val startsAt: List<Instant>,
)

data class StartPaymentRequest(
    val walletId: UUID,
    val useWallet: Boolean = true,
)

data class CancelConfirmedBookingRequest(
    @field:Size(max = 240)
    val reason: String? = null,
)

@RestController
@RequestMapping("/api/v1/bookings")
class BookingController(private val service: BookingLifecycleService) {
    @GetMapping("/{bookingId}")
    fun get(
        @PathVariable bookingId: UUID,
        @RequestHeader("X-Authenticated-User-Id") authenticatedCustomerId: UUID,
    ): BookingSnapshot = service.get(bookingId).also { requireOwner(authenticatedCustomerId, it.customerId) }

    @DeleteMapping("/{bookingId}/hold")
    fun cancelHold(
        @PathVariable bookingId: UUID,
        @RequestHeader("X-Authenticated-User-Id") authenticatedCustomerId: UUID,
    ): BookingSnapshot = service.cancel(bookingId, authenticatedCustomerId)
}

@RestController
@RequestMapping("/api/v1/bookings")
class BookingCancellationController(private val service: BookingCancellationService) {
    @GetMapping("/{bookingId}/cancellation-quote")
    suspend fun quote(
        @PathVariable bookingId: UUID,
        @RequestHeader("X-Authenticated-User-Id") authenticatedCustomerId: UUID,
    ): CancellationQuoteSnapshot = service.quote(bookingId, authenticatedCustomerId)

    @PostMapping("/{bookingId}/cancellations")
    suspend fun cancel(
        @PathVariable bookingId: UUID,
        @RequestHeader("X-Authenticated-User-Id") authenticatedCustomerId: UUID,
        @Valid @RequestBody request: CancelConfirmedBookingRequest,
    ): BookingSnapshot = service.cancel(bookingId, authenticatedCustomerId, request.reason)
}

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

@RestController
@RequestMapping("/api/v1/bookings")
class BookingPaymentController(private val paymentService: BookingPaymentService) {
    @PostMapping("/{bookingId}/payments")
    fun startPayment(
        @PathVariable bookingId: UUID,
        @RequestHeader("X-Authenticated-User-Id") authenticatedCustomerId: UUID,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @Valid @RequestBody request: StartPaymentRequest,
    ): BookingPaymentSnapshot = paymentService.start(
        StartBookingPaymentCommand(
            walletId = request.walletId,
            bookingId = bookingId,
            authenticatedCustomerId = authenticatedCustomerId,
            useWallet = request.useWallet,
            idempotencyKey = idempotencyKey,
        ),
    )
}

private fun requireOwner(authenticatedCustomerId: UUID, requestedCustomerId: UUID) {
    if (authenticatedCustomerId != requestedCustomerId) throw CustomerAccessDenied()
}

data class ConfirmPaymentRequest(
    @field:NotBlank
    val paymentReference: String,
)

data class PlatformCancellationRequest(
    @field:NotBlank @field:Size(max = 240)
    val reason: String,
)

@RestController
@RequestMapping("/internal/v1/bookings")
class InternalBookingController(private val service: BookingLifecycleService) {
    @GetMapping("/{bookingId}/plus-pass-context")
    fun plusPassContext(@PathVariable bookingId: UUID): PlusPassBookingContext = service.plusPassContext(bookingId)

    @GetMapping("/{bookingId}/ownership")
    fun ownership(@PathVariable bookingId: UUID): BookingOwnershipSnapshot = service.ownership(bookingId)

    @GetMapping("/{bookingId}/rating-context")
    fun ratingContext(@PathVariable bookingId: UUID): RatingContextSnapshot = service.ratingContext(bookingId)

    @PostMapping("/{bookingId}/platform-cancellation")
    fun platformCancellation(
        @PathVariable bookingId: UUID,
        @Valid @RequestBody request: PlatformCancellationRequest,
    ): BookingSnapshot = service.cancelConfirmedByPlatform(bookingId, request.reason)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody request: CreateBookingRequest): BookingSnapshot =
        service.create(
            CreateBookingCommand(
                customerId = request.customerId,
                serviceId = request.serviceId,
                bookingType = request.bookingType,
                duration = Duration.ofHours(request.durationHours),
                visits = request.visits.map { VisitCommand(it.workerId, it.startsAt) },
                requirements = request.requirements,
            ),
        )

    @GetMapping("/{bookingId}/payment-quote")
    fun paymentQuote(@PathVariable bookingId: UUID): PaymentQuoteSnapshot = service.paymentQuote(bookingId)

    // This route is intentionally absent from API Gateway customer routes.
    @PostMapping("/{bookingId}/payment-confirmation")
    fun confirmPayment(
        @PathVariable bookingId: UUID,
        @Valid @RequestBody request: ConfirmPaymentRequest,
    ): BookingSnapshot = service.confirmPayment(bookingId, request.paymentReference)
}
