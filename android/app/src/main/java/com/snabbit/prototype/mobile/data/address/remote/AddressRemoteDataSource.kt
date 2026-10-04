package com.snabbit.prototype.mobile.data.address.remote

interface AddressRemoteDataSource {
    suspend fun addresses(userId: String): List<AddressSnapshotDto>
    suspend fun create(userId: String, request: SaveAddressDto): AddressSnapshotDto
    suspend fun delete(userId: String, addressId: String)
}

class RetrofitAddressRemoteDataSource(
    private val api: AddressApi,
) : AddressRemoteDataSource {
    override suspend fun addresses(userId: String): List<AddressSnapshotDto> = api.addresses(userId)

    override suspend fun create(userId: String, request: SaveAddressDto): AddressSnapshotDto =
        api.create(userId, request)

    override suspend fun delete(userId: String, addressId: String) = api.delete(userId, addressId)
}
