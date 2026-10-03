package com.snabbit.prototype.user.dto.request

import com.snabbit.prototype.user.dto.response.CustomerRatingSnapshot

import com.snabbit.prototype.user.rating.CustomerRatingService
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

data class RateCustomerRequest(
    val bookingId: UUID,
    @field:Min(0) @field:Max(6)
    val visitIndex: Int,
    @field:Min(1) @field:Max(5)
    val stars: Int,
    @field:Size(max = 500)
    val comment: String? = null,
)
