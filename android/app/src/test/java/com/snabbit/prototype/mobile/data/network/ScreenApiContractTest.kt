package com.snabbit.prototype.mobile.data.network

import com.snabbit.prototype.mobile.data.address.remote.AddressApi
import com.snabbit.prototype.mobile.data.catalog.remote.CatalogApi
import com.snabbit.prototype.mobile.data.booking.remote.BookingApi
import com.snabbit.prototype.mobile.data.household.HouseholdApi
import com.snabbit.prototype.mobile.data.wallet.WalletApi
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT

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

    @Test
    fun `booking household wallet and payment paths match backend controllers`() {
        assertEquals("api/v1/bookings/availability", requireNotNull(BookingApi::class.java.methods.single { it.name == "availability" }.getAnnotation(POST::class.java)).value)
        assertEquals("api/v1/bookings/match-and-hold", requireNotNull(BookingApi::class.java.methods.single { it.name == "matchAndHold" }.getAnnotation(POST::class.java)).value)
        assertEquals("api/v1/bookings/{bookingId}", requireNotNull(BookingApi::class.java.methods.single { it.name == "booking" }.getAnnotation(GET::class.java)).value)
        assertEquals("api/v1/bookings/{bookingId}/hold", requireNotNull(BookingApi::class.java.methods.single { it.name == "cancelHold" }.getAnnotation(DELETE::class.java)).value)
        assertEquals("api/v1/bookings/{bookingId}/payments", requireNotNull(BookingApi::class.java.methods.single { it.name == "startPayment" }.getAnnotation(POST::class.java)).value)
        assertEquals("api/v1/users/{userId}/household", requireNotNull(HouseholdApi::class.java.methods.single { it.name == "get" }.getAnnotation(GET::class.java)).value)
        assertEquals("api/v1/users/{userId}/household", requireNotNull(HouseholdApi::class.java.methods.single { it.name == "update" }.getAnnotation(PUT::class.java)).value)
        assertEquals("api/v1/wallets/{walletId}", requireNotNull(WalletApi::class.java.methods.single { it.name == "balance" }.getAnnotation(GET::class.java)).value)
    }
}
