package com.snabbit.prototype.mobile.data.catalog.remote

import kotlinx.serialization.Serializable
import retrofit2.http.GET

@Serializable
data class ServiceOfferingDto(
    val serviceId: String,
    val code: String,
    val displayName: String,
    val basePricePerHourMinor: Long,
)

interface CatalogApi {
    @GET("api/v1/services")
    suspend fun services(): List<ServiceOfferingDto>
}
