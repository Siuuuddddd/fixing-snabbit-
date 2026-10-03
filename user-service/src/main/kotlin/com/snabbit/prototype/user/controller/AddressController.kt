package com.snabbit.prototype.user.controller

import com.snabbit.prototype.user.dto.response.AddressSnapshot

import com.snabbit.prototype.user.dto.request.AddressCommand

import com.snabbit.prototype.user.dto.request.SaveAddressRequest

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
@RequestMapping("/api/v1/users/{userId}/addresses")
class AddressController(private val addresses: AddressService) {
    @GetMapping
    fun list(
        @PathVariable userId: UUID,
        @RequestHeader("X-Authenticated-User-Id") authenticatedUserId: UUID,
    ): List<AddressSnapshot> = addresses.list(requireOwner(authenticatedUserId, userId))

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @PathVariable userId: UUID,
        @RequestHeader("X-Authenticated-User-Id") authenticatedUserId: UUID,
        @Valid @RequestBody request: SaveAddressRequest,
    ): AddressSnapshot = addresses.create(
        requireOwner(authenticatedUserId, userId),
        AddressCommand(
            request.label,
            request.addressLine,
            request.city,
            request.pincode,
            request.latitude,
            request.longitude,
            request.locationAccuracyMeters,
            request.locationCapturedAt,
        ),
    )

    @DeleteMapping("/{addressId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @PathVariable userId: UUID,
        @PathVariable addressId: UUID,
        @RequestHeader("X-Authenticated-User-Id") authenticatedUserId: UUID,
    ) = addresses.delete(requireOwner(authenticatedUserId, userId), addressId)

    private fun requireOwner(authenticatedUserId: UUID, requestedUserId: UUID): UUID {
        if (authenticatedUserId != requestedUserId) throw CustomerAccessDenied()
        return requestedUserId
    }
}
