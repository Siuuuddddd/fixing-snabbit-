package com.snabbit.prototype.mobile.data.catalog.remote

import kotlinx.serialization.Serializable

@Serializable
data class ServiceOfferingDto(
    val serviceId: String,
    val code: String,
    val displayName: String,
    val basePricePerHourMinor: Long,
)
