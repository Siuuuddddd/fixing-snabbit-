package com.snabbit.prototype.booking.controller

import com.snabbit.prototype.booking.domain.enums.BookingType
import com.snabbit.prototype.booking.dto.request.ConfirmPaymentRequest
import com.snabbit.prototype.booking.dto.request.CreateBookingCommand
import com.snabbit.prototype.booking.dto.request.CreateBookingRequest
import com.snabbit.prototype.booking.dto.request.PlatformCancellationRequest
import com.snabbit.prototype.booking.dto.request.VisitCommand
import com.snabbit.prototype.booking.dto.response.BookingOwnershipSnapshot
import com.snabbit.prototype.booking.dto.response.BookingSnapshot
import com.snabbit.prototype.booking.dto.response.PaymentQuoteSnapshot
import com.snabbit.prototype.booking.dto.response.PlusPassBookingContext
import com.snabbit.prototype.booking.dto.response.RatingContextSnapshot
import com.snabbit.prototype.booking.service.BookingLifecycleService
import com.snabbit.prototype.booking.service.AssignmentRecoveryService
import jakarta.validation.Valid
import java.time.Duration
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/internal/v1/bookings")
class InternalBookingController(
    private val service: BookingLifecycleService,
    private val recovery: AssignmentRecoveryService,
) {
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

    @PostMapping("/{bookingId}/visits/{visitIndex}/worker-dropout/{workerId}")
    suspend fun workerDropout(
        @PathVariable bookingId: UUID,
        @PathVariable visitIndex: Int,
        @PathVariable workerId: UUID,
    ): BookingSnapshot = recovery.recover(bookingId, visitIndex, workerId)
}
