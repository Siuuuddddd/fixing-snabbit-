package com.snabbit.prototype.booking.controller

import com.snabbit.prototype.booking.dto.request.WorkerArrivalRequest
import com.snabbit.prototype.booking.dto.request.WorkerLocationCommand
import com.snabbit.prototype.booking.dto.response.BookingSnapshot
import com.snabbit.prototype.booking.dto.response.ArrivalOtpStatusSnapshot
import com.snabbit.prototype.booking.dto.response.WorkerJobFeedItem
import com.snabbit.prototype.booking.dto.response.WorkerOfferFeedItem
import com.snabbit.prototype.booking.service.BookingLifecycleService
import com.snabbit.prototype.booking.service.WorkerFeedService
import com.snabbit.prototype.booking.service.ArrivalOtpCommandService
import jakarta.validation.Valid
import java.util.UUID
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/worker/bookings")
class WorkerJobController(
    private val bookings: BookingLifecycleService,
    private val feed: WorkerFeedService,
    private val arrivalOtp: ArrivalOtpCommandService,
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

    @PostMapping("/{bookingId}/visits/{visitIndex}/resend-arrival-otp")
    fun resendArrivalOtp(
        @PathVariable bookingId: UUID,
        @PathVariable visitIndex: Int,
        @RequestHeader("X-Authenticated-Worker-Id") workerId: UUID,
    ): ArrivalOtpStatusSnapshot = arrivalOtp.resend(bookingId, visitIndex, workerId)

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
