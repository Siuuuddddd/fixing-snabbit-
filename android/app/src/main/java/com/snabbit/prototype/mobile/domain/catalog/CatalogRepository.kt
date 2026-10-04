package com.snabbit.prototype.mobile.domain.catalog

data class ServiceOffering(
    val id: String,
    val code: String,
    val displayName: String,
    val basePricePerHourMinor: Long,
)

interface CatalogRepository {
    suspend fun services(): List<ServiceOffering>
    suspend fun service(serviceId: String): ServiceOffering?
}
