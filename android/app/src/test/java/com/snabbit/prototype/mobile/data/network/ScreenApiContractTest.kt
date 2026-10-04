package com.snabbit.prototype.mobile.data.network

import com.snabbit.prototype.mobile.data.address.remote.AddressApi
import com.snabbit.prototype.mobile.data.catalog.remote.CatalogApi
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST

class ScreenApiContractTest {
    @Test
    fun `catalogue and address paths match backend controllers`() {
        assertEquals(
            "api/v1/services",
            requireNotNull(CatalogApi::class.java.methods.single { it.name == "services" }.getAnnotation(GET::class.java)).value,
        )
        assertEquals(
            "api/v1/users/{userId}/addresses",
            requireNotNull(AddressApi::class.java.methods.single { it.name == "addresses" }.getAnnotation(GET::class.java)).value,
        )
        assertEquals(
            "api/v1/users/{userId}/addresses",
            requireNotNull(AddressApi::class.java.methods.single { it.name == "create" }.getAnnotation(POST::class.java)).value,
        )
        assertEquals(
            "api/v1/users/{userId}/addresses/{addressId}",
            requireNotNull(AddressApi::class.java.methods.single { it.name == "delete" }.getAnnotation(DELETE::class.java)).value,
        )
    }
}
