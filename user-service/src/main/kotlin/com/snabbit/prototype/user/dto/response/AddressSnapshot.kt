package com.snabbit.prototype.user.dto.response

import java.math.BigDecimal
import java.util.UUID

data class AddressSnapshot(
    val addressId: UUID,
    val userId: UUID,
    val label: String,
    val addressLine: String,
    val city: String,
    val pincode: String,
    val latitude: BigDecimal,
    val longitude: BigDecimal,
    val verified: Boolean,
)
