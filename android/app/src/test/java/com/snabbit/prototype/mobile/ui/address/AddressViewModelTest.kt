package com.snabbit.prototype.mobile.ui.address

import com.snabbit.prototype.mobile.domain.address.AddressRepository
import com.snabbit.prototype.mobile.domain.address.DeviceLocationDataSource
import com.snabbit.prototype.mobile.domain.address.LocationSample
import com.snabbit.prototype.mobile.domain.address.NewAddress
import com.snabbit.prototype.mobile.domain.address.SavedAddress
import com.snabbit.prototype.mobile.domain.common.DataFailure
import com.snabbit.prototype.mobile.domain.common.DataFailureReason
import com.snabbit.prototype.mobile.ui.common.UiDataErrorReason
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AddressViewModelTest {
    @Test
    fun `loads empty saved address state`() = runTest {
        val viewModel = viewModel(FakeAddressRepository(), testScheduler)

        runCurrent()

        assertTrue(viewModel.uiState.value.addresses.isEmpty())
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(null, viewModel.uiState.value.error)
    }

    @Test
    fun `captures accurate location and saves verified address`() = runTest {
        val repository = FakeAddressRepository()
        val viewModel = viewModel(repository, testScheduler)
        runCurrent()
        viewModel.showAddForm()
        viewModel.updateLabel("Home")
        viewModel.updateAddressLine("1 Marine Drive")
        viewModel.updatePincode("400001")

        viewModel.captureLocation()
        runCurrent()
        viewModel.saveAddress()
        runCurrent()

        assertEquals("Home", repository.created?.label)
        assertEquals("400001", repository.created?.pincode)
        assertEquals(1, viewModel.uiState.value.addresses.size)
        assertFalse(viewModel.uiState.value.showAddForm)
    }

    @Test
    fun `inaccurate location blocks address save`() = runTest {
        val repository = FakeAddressRepository()
        val viewModel = viewModel(repository, testScheduler, locationSample(accuracy = 140.0))
        runCurrent()
        viewModel.showAddForm()
        viewModel.updateLabel("Home")
        viewModel.updateAddressLine("1 Marine Drive")
        viewModel.updatePincode("400001")

        viewModel.captureLocation()
        runCurrent()
        viewModel.saveAddress()

        assertEquals(AddressFormError.LOCATION_INACCURATE, viewModel.uiState.value.formError)
        assertEquals(null, repository.created)
    }

    @Test
    fun `deletes address after backend confirms`() = runTest {
        val address = savedAddress()
        val repository = FakeAddressRepository(initial = mutableListOf(address))
        val viewModel = viewModel(repository, testScheduler)
        runCurrent()

        viewModel.deleteAddress(address.id)
        runCurrent()

        assertEquals(address.id, repository.deletedId)
        assertTrue(viewModel.uiState.value.addresses.isEmpty())
    }

    @Test
    fun `offline list failure exposes recoverable error`() = runTest {
        val viewModel = viewModel(
            FakeAddressRepository(listFailure = DataFailure(DataFailureReason.OFFLINE)),
            testScheduler,
        )

        runCurrent()

        assertEquals(UiDataErrorReason.OFFLINE, viewModel.uiState.value.error?.reason)
    }

    private fun viewModel(
        repository: FakeAddressRepository,
        scheduler: kotlinx.coroutines.test.TestCoroutineScheduler,
        location: LocationSample = locationSample(),
    ): AddressViewModel = AddressViewModel(
        repository,
        FakeLocationDataSource(location),
        StandardTestDispatcher(scheduler),
    )
}

private class FakeAddressRepository(
    private val initial: MutableList<SavedAddress> = mutableListOf(),
    private val listFailure: Throwable? = null,
) : AddressRepository {
    var created: NewAddress? = null
    var deletedId: String? = null

    override suspend fun addresses(): List<SavedAddress> {
        listFailure?.let { throw it }
        return initial.toList()
    }

    override suspend fun create(address: NewAddress): SavedAddress {
        created = address
        return savedAddress(label = address.label, pincode = address.pincode).also(initial::add)
    }

    override suspend fun delete(addressId: String) {
        deletedId = addressId
        initial.removeAll { it.id == addressId }
    }
}

private class FakeLocationDataSource(private val location: LocationSample) : DeviceLocationDataSource {
    override suspend fun currentLocation(): LocationSample = location
}

private fun locationSample(accuracy: Double = 20.0) = LocationSample(
    latitude = 18.944,
    longitude = 72.823,
    accuracyMeters = accuracy,
    capturedAtIso = "2026-10-04T10:00:00Z",
)

private fun savedAddress(label: String = "Home", pincode: String = "400001") = SavedAddress(
    id = "address-id",
    label = label,
    addressLine = "1 Marine Drive",
    city = "Mumbai",
    pincode = pincode,
    latitude = 18.944,
    longitude = 72.823,
    verified = true,
)
