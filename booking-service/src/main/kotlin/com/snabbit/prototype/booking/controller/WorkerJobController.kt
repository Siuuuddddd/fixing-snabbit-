package com.snabbit.prototype.booking.controller

import com.snabbit.prototype.booking.service.BookingLifecycleService
import com.snabbit.prototype.booking.service.BookingSnapshot
import com.snabbit.prototype.booking.service.WorkerLocationCommand
import com.snabbit.prototype.booking.service.WorkerFeedService
import com.snabbit.prototype.booking.service.WorkerJobFeedItem
import com.snabbit.prototype.booking.service.WorkerOfferFeedItem
import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Positive
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.util.UUID

data class WorkerArrivalRequest(
    @field:DecimalMin("-90.0") @field:DecimalMax("90.0")
    val latitude: BigDecimal,
    @field:DecimalMin("-180.0") @field:DecimalMax("180.0")
    val longitude: BigDecimal,
    @field:Positive
    val accuracyMeters: BigDecimal,
)

@RestController
@RequestMapping("/api/v1/worker/bookings")
class WorkerJobController(
    private val bookings: BookingLifecycleService,
    private val feed: WorkerFeedService,
) {
    @GetMapping("/offers")
    fun offers(
        @RequestHeader("X-Authenticated-Worker-Id") workerId: UUID,
    ): List<WorkerOfferFeedItem> = feed.pendingOffers(workerId)

    @GetMapping("/jobs")
    fun jobs(
        @RequestHeader("X-Authenticated-Worker-Id") workerId: UUID,
    ): List<WorkerJobFeedItem> = feed.assignedJobs(workerId)

    @PostMapping("/{bookingId}/offers/{offerId}/accept")
    fun accept(
        @PathVariable bookingId: UUID,
        @PathVariable offerId: UUID,
        @RequestHeader("X-Authenticated-Worker-Id") workerId: UUID,
    ): BookingSnapshot = bookings.acceptOffer(bookingId, offerId, workerId)

    @PostMapping("/{bookingId}/offers/{offerId}/reject")
    fun reject(
        @PathVariable bookingId: UUID,
        @PathVariable offerId: UUID,
        @RequestHeader("X-Authenticated-Worker-Id") workerId: UUID,
    ): BookingSnapshot = bookings.rejectOffer(bookingId, offerId, workerId)

    @PostMapping("/{bookingId}/visits/{visitIndex}/arrive")
    fun arrive(
        @PathVariable bookingId: UUID,
        @PathVariable visitIndex: Int,
        @RequestHeader("X-Authenticated-Worker-Id") workerId: UUID,
        @Valid @RequestBody request: WorkerArrivalRequest,
    ): BookingSnapshot = bookings.workerArrive(
        bookingId,
        visitIndex,
        workerId,
        WorkerLocationCommand(request.latitude, request.longitude, request.accuracyMeters),
    )

    @PostMapping("/{bookingId}/visits/{visitIndex}/start")
    fun start(
        @PathVariable bookingId: UUID,
        @PathVariable visitIndex: Int,
        @RequestHeader("X-Authenticated-Worker-Id") workerId: UUID,
    ): BookingSnapshot = bookings.workerStart(bookingId, visitIndex, workerId)

    @PostMapping("/{bookingId}/visits/{visitIndex}/complete")
    fun complete(
        @PathVariable bookingId: UUID,
        @PathVariable visitIndex: Int,
        @RequestHeader("X-Authenticated-Worker-Id") workerId: UUID,
    ): BookingSnapshot = bookings.workerComplete(bookingId, visitIndex, workerId)
}
