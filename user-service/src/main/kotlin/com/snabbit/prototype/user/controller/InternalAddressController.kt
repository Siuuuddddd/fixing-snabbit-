package com.snabbit.prototype.user.controller

import com.snabbit.prototype.user.dto.response.AddressSnapshot
import com.snabbit.prototype.user.service.AddressService
import java.util.UUID
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/internal/v1/users/{userId}/addresses")
class InternalAddressController(private val addresses: AddressService) {
    @GetMapping("/{addressId}")
    fun requireOwnedVerified(
        @PathVariable userId: UUID,
        @PathVariable addressId: UUID,
    ): AddressSnapshot = addresses.requireOwnedVerified(userId, addressId)
}
