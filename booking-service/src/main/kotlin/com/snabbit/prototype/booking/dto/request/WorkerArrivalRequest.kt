package com.snabbit.prototype.booking.dto.request

import com.snabbit.prototype.booking.dto.response.WorkerOfferFeedItem

import com.snabbit.prototype.booking.dto.response.WorkerJobFeedItem

import com.snabbit.prototype.booking.dto.response.BookingSnapshot

import com.snabbit.prototype.booking.dto.request.WorkerLocationCommand

import com.snabbit.prototype.booking.service.BookingLifecycleService
import com.snabbit.prototype.booking.service.WorkerFeedService
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
