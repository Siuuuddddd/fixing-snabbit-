package com.snabbit.prototype.user.controller

import com.snabbit.prototype.user.service.AddressCommand
import com.snabbit.prototype.user.service.AddressService
import com.snabbit.prototype.user.service.AddressSnapshot
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

@RestController
@RequestMapping("/internal/v1/users/{userId}/addresses")
class InternalAddressController(private val addresses: AddressService) {
    @GetMapping("/{addressId}")
    fun requireOwnedVerified(
        @PathVariable userId: UUID,
        @PathVariable addressId: UUID,
    ): AddressSnapshot = addresses.requireOwnedVerified(userId, addressId)
}
