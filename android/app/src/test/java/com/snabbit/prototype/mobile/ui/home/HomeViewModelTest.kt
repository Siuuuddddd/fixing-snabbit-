package com.snabbit.prototype.mobile.ui.home

import com.snabbit.prototype.mobile.domain.address.AddressRepository
import com.snabbit.prototype.mobile.domain.address.NewAddress
import com.snabbit.prototype.mobile.domain.address.SavedAddress
import com.snabbit.prototype.mobile.domain.catalog.CatalogRepository
import com.snabbit.prototype.mobile.domain.catalog.ServiceOffering
import com.snabbit.prototype.mobile.domain.common.DataFailure
import com.snabbit.prototype.mobile.domain.common.DataFailureReason
import com.snabbit.prototype.mobile.domain.home.FavoriteExpertsPreview
import com.snabbit.prototype.mobile.domain.home.GetFavoritesUseCase
import com.snabbit.prototype.mobile.domain.home.GetHabitPackPromoUseCase
import com.snabbit.prototype.mobile.domain.home.GetHomeServicesUseCase
import com.snabbit.prototype.mobile.domain.home.GetSelectedAddressUseCase
import com.snabbit.prototype.mobile.domain.home.HabitPackPromo
import com.snabbit.prototype.mobile.domain.home.HabitPackPromoRepository
import com.snabbit.prototype.mobile.domain.home.HomeFavoritesRepository
import com.snabbit.prototype.mobile.ui.common.UiDataErrorReason
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    @Test
    fun `loads catalogue address favorites and promo on start`() = runTest {
        val address = savedAddress(verified = true)
        val viewModel = viewModel(
            catalog = FakeCatalogRepository(listOf(serviceOffering())),
            addresses = FakeAddressRepository(listOf(address)),
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        runCurrent()

        assertEquals(6, viewModel.uiState.value.services.size)
        assertEquals(address, viewModel.uiState.value.selectedAddress)
        assertEquals(1, viewModel.uiState.value.favorites?.remainingExpertsToUnlock)
        assertEquals(27_900L, viewModel.uiState.value.promo?.offerPriceMinor)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `empty catalogue is an explicit content state`() = runTest {
        val viewModel = viewModel(
            catalog = FakeCatalogRepository(emptyList()),
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        runCurrent()

        assertEquals(0, viewModel.uiState.value.services.size)
        assertEquals(null, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `offline failure exposes recoverable error`() = runTest {
        val viewModel = viewModel(
            catalog = FakeCatalogRepository(failure = DataFailure(DataFailureReason.OFFLINE)),
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        runCurrent()

        assertEquals(UiDataErrorReason.OFFLINE, viewModel.uiState.value.error?.reason)
    }

    @Test
    fun `pull refresh replaces catalogue content`() = runTest {
        val repository = SequencedCatalogRepository(
            mutableListOf(
                listOf(serviceOffering(id = "one")),
                listOf(serviceOffering(id = "two")),
            ),
        )
        val viewModel = viewModel(repository, dispatcher = StandardTestDispatcher(testScheduler))
        runCurrent()

        viewModel.onEvent(HomeUiEvent.Refresh)
        runCurrent()

        assertEquals("two", viewModel.uiState.value.services.first().serviceId)
        assertFalse(viewModel.uiState.value.isRefreshing)
    }

    @Test
    fun `booking mode events update preferred mode`() = runTest {
        val viewModel = viewModel(
            catalog = FakeCatalogRepository(listOf(serviceOffering())),
            dispatcher = StandardTestDispatcher(testScheduler),
        )
        runCurrent()

        viewModel.onEvent(HomeUiEvent.ScheduleSelected)
        assertEquals(HomeBookingMode.SCHEDULED, viewModel.uiState.value.preferredBookingMode)

        viewModel.onEvent(HomeUiEvent.InstantSelected)
        assertEquals(HomeBookingMode.INSTANT, viewModel.uiState.value.preferredBookingMode)
    }
}

private fun viewModel(
    catalog: CatalogRepository,
    addresses: AddressRepository = FakeAddressRepository(emptyList()),
    dispatcher: kotlinx.coroutines.CoroutineDispatcher,
) = HomeViewModel(
    getHomeServices = GetHomeServicesUseCase(catalog),
    getSelectedAddress = GetSelectedAddressUseCase(addresses),
    getFavorites = GetFavoritesUseCase(FakeFavoritesRepository()),
    getHabitPackPromo = GetHabitPackPromoUseCase(FakePromoRepository()),
    dispatcher = dispatcher,
)

private class FakeCatalogRepository(
    private val services: List<ServiceOffering> = emptyList(),
    private val failure: Throwable? = null,
) : CatalogRepository {
    override suspend fun services(): List<ServiceOffering> {
        failure?.let { throw it }
        return services
    }

    override suspend fun service(serviceId: String): ServiceOffering? = services.firstOrNull { it.id == serviceId }
}

private class SequencedCatalogRepository(
    private val responses: MutableList<List<ServiceOffering>>,
) : CatalogRepository {
    override suspend fun services(): List<ServiceOffering> = responses.removeFirst()
    override suspend fun service(serviceId: String): ServiceOffering? = null
}

private class FakeAddressRepository(private val saved: List<SavedAddress>) : AddressRepository {
    override suspend fun addresses() = saved
    override suspend fun create(address: NewAddress) = error("Not used")
    override suspend fun delete(addressId: String) = Unit
}

private class FakeFavoritesRepository : HomeFavoritesRepository {
    override suspend fun preview() = FavoriteExpertsPreview(1, 3)
}

private class FakePromoRepository : HabitPackPromoRepository {
    override suspend fun promo() = HabitPackPromo(3, 89_700, 27_900)
}

internal fun serviceOffering(
    id: String = "service-id",
    code: String = "HOME_CLEANING",
) = ServiceOffering(id, code, code, 29_900)

internal fun savedAddress(id: String = "address-id", verified: Boolean = false) = SavedAddress(
    id = id,
    label = "Home",
    addressLine = "D711, New Usha Nagar",
    city = "Mumbai",
    pincode = "400001",
    latitude = 19.0,
    longitude = 72.0,
    verified = verified,
)
