package com.snabbit.prototype.worker.api

import com.snabbit.prototype.worker.rating.WorkerRatingService
import com.snabbit.prototype.worker.rating.WorkerRatingSnapshot
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

data class RateWorkerRequest(
    val bookingId: UUID,
    @field:Min(0) @field:Max(6)
    val visitIndex: Int,
    @field:Min(1) @field:Max(5)
    val stars: Int,
    @field:Size(max = 500)
    val comment: String? = null,
)

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
