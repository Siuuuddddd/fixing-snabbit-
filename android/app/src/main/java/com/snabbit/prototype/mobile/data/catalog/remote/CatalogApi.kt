package com.snabbit.prototype.mobile.data.catalog.remote

import retrofit2.http.GET

interface CatalogApi {
    @GET("api/v1/services")
    suspend fun services(): List<ServiceOfferingDto>
}
