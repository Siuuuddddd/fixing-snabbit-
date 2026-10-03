package com.snabbit.prototype.user.controller

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

@RestController
@RequestMapping("/internal/v1/users/{userId}/addresses")
class InternalAddressController(private val addresses: AddressService) {
    @GetMapping("/{addressId}")
    fun requireOwnedVerified(
        @PathVariable userId: UUID,
        @PathVariable addressId: UUID,
    ): AddressSnapshot = addresses.requireOwnedVerified(userId, addressId)
}
