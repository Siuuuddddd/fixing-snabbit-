package com.snabbit.prototype.mobile.domain.home

import com.snabbit.prototype.mobile.domain.address.AddressRepository
import com.snabbit.prototype.mobile.domain.address.NewAddress
import com.snabbit.prototype.mobile.domain.address.SavedAddress
import com.snabbit.prototype.mobile.domain.catalog.CatalogRepository
import com.snabbit.prototype.mobile.domain.catalog.ServiceOffering
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeUseCasesTest {
    @Test
    fun `home services map live catalogue ids to the reference categories`() = runTest {
        val repository = FakeCatalogRepository(
            listOf(
                service("home", "HOME_CLEANING"),
                service("bathroom", "TOILET_CLEANING"),
                service("laundry", "LAUNDRY"),
                service("dishes", "DISH_WASHING"),
                service("extra", "WINDOW_CLEANING"),
            ),
        )

        val result = GetHomeServicesUseCase(repository)()

        assertEquals(HomeServiceCategory.entries, result.map { it.category })
        assertEquals(listOf("home", "home", "bathroom", "laundry", "dishes", "extra"), result.map { it.serviceId })
    }

    @Test
    fun `empty live catalogue stays empty`() = runTest {
        assertEquals(emptyList<HomeServiceTile>(), GetHomeServicesUseCase(FakeCatalogRepository(emptyList()))())
    }

    @Test
    fun `selected address prefers first verified address`() = runTest {
        val unverified = address("one", verified = false)
        val verified = address("two", verified = true)

        val selected = GetSelectedAddressUseCase(FakeAddressRepository(listOf(unverified, verified)))()

        assertEquals(verified, selected)
    }

    @Test
    fun `selected address is null when backend has none`() = runTest {
        assertNull(GetSelectedAddressUseCase(FakeAddressRepository(emptyList()))())
    }

    @Test
    fun `favorites and promo use cases delegate to repositories`() = runTest {
        val favorites = FavoriteExpertsPreview(2, 3)
        val promo = HabitPackPromo(3, 89_700, 27_900)

        assertEquals(favorites, GetFavoritesUseCase(object : HomeFavoritesRepository {
            override suspend fun preview() = favorites
        })())
        assertEquals(promo, GetHabitPackPromoUseCase(object : HabitPackPromoRepository {
            override suspend fun promo() = promo
        })())
    }
}

private class FakeCatalogRepository(private val values: List<ServiceOffering>) : CatalogRepository {
    override suspend fun services() = values
    override suspend fun service(serviceId: String) = values.firstOrNull { it.id == serviceId }
}

private class FakeAddressRepository(private val values: List<SavedAddress>) : AddressRepository {
    override suspend fun addresses() = values
    override suspend fun create(address: NewAddress) = error("Not used")
    override suspend fun delete(addressId: String) = Unit
}

private fun service(id: String, code: String) = ServiceOffering(id, code, code, 1_000)

private fun address(id: String, verified: Boolean) = SavedAddress(
    id, "Home", "Address", "Mumbai", "400001", 19.0, 72.0, verified,
)
