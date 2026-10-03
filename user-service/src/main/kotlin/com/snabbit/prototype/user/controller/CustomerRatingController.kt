package com.snabbit.prototype.user.controller

import com.snabbit.prototype.user.dto.request.RateCustomerRequest
import com.snabbit.prototype.user.dto.response.CustomerRatingSnapshot
import com.snabbit.prototype.user.rating.CustomerRatingService
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
@RequestMapping("/api/v1/worker/ratings/customers")
class CustomerRatingController(private val ratings: CustomerRatingService) {
    @PostMapping("/{customerId}")
    @ResponseStatus(HttpStatus.CREATED)
    fun rate(
        @PathVariable customerId: UUID,
        @RequestHeader("X-Authenticated-Worker-Id") workerId: UUID,
        @Valid @RequestBody request: RateCustomerRequest,
    ): CustomerRatingSnapshot = ratings.rate(
        workerId,
        customerId,
        request.bookingId,
        request.visitIndex,
        request.stars,
        request.comment,
    )
}
