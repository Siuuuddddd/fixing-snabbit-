package com.snabbit.prototype.worker.controller

import com.snabbit.prototype.worker.dto.request.RateWorkerRequest
import com.snabbit.prototype.worker.dto.response.WorkerRatingSnapshot
import com.snabbit.prototype.worker.rating.WorkerRatingService
import jakarta.validation.Valid
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/ratings/workers")
class WorkerRatingController(private val ratings: WorkerRatingService) {
    @PostMapping("/{workerId}")
    @ResponseStatus(HttpStatus.CREATED)
    fun rate(
        @PathVariable workerId: UUID,
        @RequestHeader("X-Authenticated-User-Id") customerId: UUID,
        @Valid @RequestBody request: RateWorkerRequest,
    ): WorkerRatingSnapshot = ratings.rate(
        customerId,
        workerId,
        request.bookingId,
        request.visitIndex,
        request.stars,
        request.comment,
    )
}
