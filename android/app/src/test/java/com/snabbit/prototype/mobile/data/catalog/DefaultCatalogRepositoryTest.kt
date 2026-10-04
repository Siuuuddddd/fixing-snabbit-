package com.snabbit.prototype.mobile.data.catalog

import com.snabbit.prototype.mobile.data.catalog.remote.CatalogRemoteDataSource
import com.snabbit.prototype.mobile.data.catalog.remote.ServiceOfferingDto
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultCatalogRepositoryTest {
    @Test
    fun `maps exact backend catalogue fields to domain`() = runTest {
        val repository = DefaultCatalogRepository(
            FakeCatalogRemoteDataSource(
                listOf(ServiceOfferingDto("service-id", "HOME_CLEANING", "Home Cleaning", 29_900)),
            ),
            Json,
        )

        val service = repository.services().single()

        assertEquals("service-id", service.id)
        assertEquals("HOME_CLEANING", service.code)
        assertEquals(29_900, service.basePricePerHourMinor)
    }
}

private class FakeCatalogRemoteDataSource(
    private val response: List<ServiceOfferingDto>,
) : CatalogRemoteDataSource {
    override suspend fun services(): List<ServiceOfferingDto> = response
}
