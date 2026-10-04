package com.snabbit.prototype.mobile.data.address

import com.snabbit.prototype.mobile.data.address.remote.AddressRemoteDataSource
import com.snabbit.prototype.mobile.data.address.remote.AddressSnapshotDto
import com.snabbit.prototype.mobile.data.address.remote.SaveAddressDto
import com.snabbit.prototype.mobile.data.network.apiCall
import com.snabbit.prototype.mobile.domain.address.AddressRepository
import com.snabbit.prototype.mobile.domain.address.NewAddress
import com.snabbit.prototype.mobile.domain.address.SavedAddress
import com.snabbit.prototype.mobile.domain.common.DataFailure
import com.snabbit.prototype.mobile.domain.common.DataFailureReason
import com.snabbit.prototype.mobile.domain.session.SessionIdentityProvider
import kotlinx.serialization.json.Json

class DefaultAddressRepository(
    private val remote: AddressRemoteDataSource,
    private val identity: SessionIdentityProvider,
    private val json: Json,
) : AddressRepository {
    override suspend fun addresses(): List<SavedAddress> = apiCall(json) {
        remote.addresses(requireCustomerId()).map { dto -> dto.toDomain() }
    }

    override suspend fun create(address: NewAddress): SavedAddress = apiCall(json) {
        remote.create(
            requireCustomerId(),
            SaveAddressDto(
                label = address.label,
                addressLine = address.addressLine,
                city = MUMBAI,
                pincode = address.pincode,
                latitude = address.location.latitude,
                longitude = address.location.longitude,
                locationAccuracyMeters = address.location.accuracyMeters,
                locationCapturedAt = address.location.capturedAtIso,
            ),
        ).toDomain()
    }

    override suspend fun delete(addressId: String) = apiCall(json) {
        remote.delete(requireCustomerId(), addressId)
    }

    private fun requireCustomerId(): String = identity.customerId()
        ?: throw DataFailure(DataFailureReason.UNAUTHORIZED)

    private fun AddressSnapshotDto.toDomain() = SavedAddress(
        id = addressId,
        label = label,
        addressLine = addressLine,
        city = city,
        pincode = pincode,
        latitude = latitude,
        longitude = longitude,
        verified = verified,
    )

    private companion object {
        const val MUMBAI = "Mumbai"
    }
}
