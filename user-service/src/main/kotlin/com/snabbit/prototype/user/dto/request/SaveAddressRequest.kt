package com.snabbit.prototype.user.dto.request

import com.snabbit.prototype.user.dto.response.AddressSnapshot

import com.snabbit.prototype.user.dto.request.AddressCommand

import com.snabbit.prototype.user.service.AddressService
import com.snabbit.prototype.user.support.CustomerAccessDenied
import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Positive
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
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class SaveAddressRequest(
    @field:Size(min = 1, max = 60) val label: String,
    @field:Size(min = 5, max = 300) val addressLine: String,
    @field:Size(min = 1, max = 80) val city: String,
    @field:Pattern(regexp = "^400[0-9]{3}$") val pincode: String,
    @field:DecimalMin("-90.0") @field:DecimalMax("90.0") val latitude: BigDecimal,
    @field:DecimalMin("-180.0") @field:DecimalMax("180.0") val longitude: BigDecimal,
    @field:Positive val locationAccuracyMeters: BigDecimal,
    val locationCapturedAt: Instant,
)
