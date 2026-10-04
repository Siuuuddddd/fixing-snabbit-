package com.snabbit.prototype.mobile.data.address.remote

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

@Serializable
data class SaveAddressDto(
    val label: String,
    val addressLine: String,
    val city: String,
    val pincode: String,
    val latitude: Double,
    val longitude: Double,
    val locationAccuracyMeters: Double,
    val locationCapturedAt: String,
)

@Serializable
data class AddressSnapshotDto(
    val addressId: String,
    val userId: String,
    val label: String,
    val addressLine: String,
    val city: String,
    val pincode: String,
    val latitude: Double,
    val longitude: Double,
    val verified: Boolean,
)

interface AddressApi {
    @GET("api/v1/users/{userId}/addresses")
    suspend fun addresses(@Path("userId") userId: String): List<AddressSnapshotDto>

    @POST("api/v1/users/{userId}/addresses")
    suspend fun create(
        @Path("userId") userId: String,
        @Body request: SaveAddressDto,
    ): AddressSnapshotDto

    @DELETE("api/v1/users/{userId}/addresses/{addressId}")
    suspend fun delete(
        @Path("userId") userId: String,
        @Path("addressId") addressId: String,
    )
}
