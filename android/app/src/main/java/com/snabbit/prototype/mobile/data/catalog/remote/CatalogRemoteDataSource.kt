package com.snabbit.prototype.mobile.data.catalog.remote

interface CatalogRemoteDataSource {
    suspend fun services(): List<ServiceOfferingDto>
}

class RetrofitCatalogRemoteDataSource(
    private val api: CatalogApi,
) : CatalogRemoteDataSource {
    override suspend fun services(): List<ServiceOfferingDto> = api.services()
}
