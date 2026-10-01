package com.snabbit.prototype.booking.api

import com.snabbit.prototype.booking.application.BookingLifecycleService
import com.snabbit.prototype.booking.application.BookingSnapshot
import com.snabbit.prototype.booking.application.CreateBookingCommand
import com.snabbit.prototype.booking.application.VisitCommand
import com.snabbit.prototype.booking.domain.BookingType
import com.snabbit.prototype.booking.domain.BookingRequirements
import com.snabbit.prototype.booking.matching.MatchAndHoldCommand
import com.snabbit.prototype.booking.matching.MatchAndHoldService
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
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
    @field:NotBlank
    val serviceCode: String,
    val bookingType: BookingType,
    @field:Min(1) @field:Max(4)
    val durationHours: Long,
    @field:DecimalMin("-90.0") @field:DecimalMax("90.0")
    val latitude: Double,
    @field:DecimalMin("-180.0") @field:DecimalMax("180.0")
    val longitude: Double,
    @field:NotEmpty @field:Size(max = 7)
    val startsAt: List<Instant>,
)

data class WorkerDecisionRequest(val workerId: UUID)

@RestController
@RequestMapping("/api/v1/bookings")
class BookingController(private val service: BookingLifecycleService) {
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

    @GetMapping("/{bookingId}")
    fun get(@PathVariable bookingId: UUID): BookingSnapshot = service.get(bookingId)

    @PostMapping("/{bookingId}/worker-offers/{offerId}/accept")
    fun acceptOffer(
        @PathVariable bookingId: UUID,
        @PathVariable offerId: UUID,
        @Valid @RequestBody request: WorkerDecisionRequest,
    ): BookingSnapshot = service.acceptOffer(bookingId, offerId, request.workerId)

    @PostMapping("/{bookingId}/worker-offers/{offerId}/reject")
    fun rejectOffer(
        @PathVariable bookingId: UUID,
        @PathVariable offerId: UUID,
        @Valid @RequestBody request: WorkerDecisionRequest,
    ): BookingSnapshot = service.rejectOffer(bookingId, offerId, request.workerId)

    @DeleteMapping("/{bookingId}/hold")
    fun cancelHold(@PathVariable bookingId: UUID): BookingSnapshot = service.cancel(bookingId)
}

@RestController
@RequestMapping("/api/v1/bookings")
class MatchAndHoldController(private val service: MatchAndHoldService) {
    @PostMapping("/match-and-hold")
    @ResponseStatus(HttpStatus.CREATED)
    suspend fun matchAndHold(@Valid @RequestBody request: MatchAndHoldRequest): BookingSnapshot =
        service.matchAndHold(
            MatchAndHoldCommand(
                customerId = request.customerId,
                serviceId = request.serviceId,
                serviceCode = request.serviceCode,
                bookingType = request.bookingType,
                duration = Duration.ofHours(request.durationHours),
                latitude = request.latitude,
                longitude = request.longitude,
                startsAt = request.startsAt,
            ),
        )
}

data class ConfirmPaymentRequest(
    @field:NotBlank
    val paymentReference: String,
)

@RestController
@RequestMapping("/internal/v1/bookings")
class InternalBookingController(private val service: BookingLifecycleService) {
    // This route is intentionally absent from API Gateway customer routes.
    @PostMapping("/{bookingId}/payment-confirmation")
    fun confirmPayment(
        @PathVariable bookingId: UUID,
        @Valid @RequestBody request: ConfirmPaymentRequest,
    ): BookingSnapshot = service.confirmPayment(bookingId, request.paymentReference)
}
