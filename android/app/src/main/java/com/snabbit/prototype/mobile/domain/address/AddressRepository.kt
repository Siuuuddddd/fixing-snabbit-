package com.snabbit.prototype.mobile.domain.address

data class SavedAddress(
    val id: String,
    val label: String,
    val addressLine: String,
    val city: String,
    val pincode: String,
    val latitude: Double,
    val longitude: Double,
    val verified: Boolean,
)

data class LocationSample(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Double,
    val capturedAtIso: String,
)

data class NewAddress(
    val label: String,
    val addressLine: String,
    val pincode: String,
    val location: LocationSample,
)

interface AddressRepository {
    suspend fun addresses(): List<SavedAddress>
    suspend fun create(address: NewAddress): SavedAddress
    suspend fun delete(addressId: String)
}

interface DeviceLocationDataSource {
    suspend fun currentLocation(): LocationSample
}

enum class LocationFailureReason {
    PERMISSION_REQUIRED,
    UNAVAILABLE,
    TIMEOUT,
}

class LocationFailure(val reason: LocationFailureReason) : Exception()
