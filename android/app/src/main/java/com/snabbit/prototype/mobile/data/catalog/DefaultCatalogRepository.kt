package com.snabbit.prototype.mobile.data.catalog

import com.snabbit.prototype.mobile.data.catalog.remote.CatalogRemoteDataSource
import com.snabbit.prototype.mobile.data.catalog.mapper.toDomain
import com.snabbit.prototype.mobile.data.network.apiCall
import com.snabbit.prototype.mobile.domain.catalog.CatalogRepository
import com.snabbit.prototype.mobile.domain.catalog.ServiceOffering
import kotlinx.serialization.json.Json

class DefaultCatalogRepository(
    private val remote: CatalogRemoteDataSource,
    private val json: Json,
) : CatalogRepository {
    override suspend fun services(): List<ServiceOffering> = apiCall(json) {
        remote.services().map { dto -> dto.toDomain() }
    }

    override suspend fun service(serviceId: String): ServiceOffering? =
        services().firstOrNull { it.id == serviceId }
}
