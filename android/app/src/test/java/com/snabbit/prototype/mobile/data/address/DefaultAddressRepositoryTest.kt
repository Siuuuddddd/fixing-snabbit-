package com.snabbit.prototype.mobile.data.address

import com.snabbit.prototype.mobile.data.address.remote.AddressRemoteDataSource
import com.snabbit.prototype.mobile.data.address.remote.AddressSnapshotDto
import com.snabbit.prototype.mobile.data.address.remote.SaveAddressDto
import com.snabbit.prototype.mobile.domain.address.LocationSample
import com.snabbit.prototype.mobile.domain.address.NewAddress
import com.snabbit.prototype.mobile.domain.session.SessionIdentityProvider
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultAddressRepositoryTest {
    @Test
    fun `create sends jwt subject and exact serviceability fields`() = runTest {
        val remote = RecordingAddressRemoteDataSource()
        val repository = DefaultAddressRepository(
            remote,
            SessionIdentityProvider { CUSTOMER_ID },
            Json,
        )
        val location = LocationSample(18.944, 72.823, 18.0, "2026-10-04T10:00:00Z")

        repository.create(NewAddress("Home", "1 Marine Drive", "400001", location))

        assertEquals(CUSTOMER_ID, remote.userId)
        assertEquals("Mumbai", remote.request?.city)
        assertEquals("400001", remote.request?.pincode)
        assertEquals(18.0, remote.request?.locationAccuracyMeters)
        assertEquals("2026-10-04T10:00:00Z", remote.request?.locationCapturedAt)
    }

    private companion object {
        const val CUSTOMER_ID = "10000000-0000-0000-0000-000000000001"
    }
}

private class RecordingAddressRemoteDataSource : AddressRemoteDataSource {
    var userId: String? = null
    var request: SaveAddressDto? = null

    override suspend fun addresses(userId: String): List<AddressSnapshotDto> = emptyList()

    override suspend fun create(userId: String, request: SaveAddressDto): AddressSnapshotDto {
        this.userId = userId
        this.request = request
        return AddressSnapshotDto(
            "address-id",
            userId,
            request.label,
            request.addressLine,
            request.city,
            request.pincode,
            request.latitude,
            request.longitude,
            true,
        )
    }

    override suspend fun delete(userId: String, addressId: String) = Unit
}
