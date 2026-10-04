package com.snabbit.prototype.mobile.ui.address

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snabbit.prototype.mobile.domain.address.AddressRepository
import com.snabbit.prototype.mobile.domain.address.DeviceLocationDataSource
import com.snabbit.prototype.mobile.domain.address.LocationFailure
import com.snabbit.prototype.mobile.domain.address.LocationFailureReason
import com.snabbit.prototype.mobile.domain.address.LocationSample
import com.snabbit.prototype.mobile.domain.address.NewAddress
import com.snabbit.prototype.mobile.domain.address.SavedAddress
import com.snabbit.prototype.mobile.ui.common.UiDataError
import com.snabbit.prototype.mobile.ui.common.toUiDataError
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AddressFormError {
    INVALID_LABEL,
    INVALID_ADDRESS,
    INVALID_PINCODE,
    LOCATION_REQUIRED,
    LOCATION_INACCURATE,
    LOCATION_PERMISSION,
    LOCATION_UNAVAILABLE,
    LOCATION_TIMEOUT,
}

data class AddressUiState(
    val addresses: List<SavedAddress> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isSaving: Boolean = false,
    val isLocating: Boolean = false,
    val deletingAddressId: String? = null,
    val showAddForm: Boolean = false,
    val label: String = "",
    val addressLine: String = "",
    val pincode: String = "",
    val location: LocationSample? = null,
    val error: UiDataError? = null,
    val formError: AddressFormError? = null,
)

class AddressViewModel(
    private val addressRepository: AddressRepository,
    private val locationDataSource: DeviceLocationDataSource,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AddressUiState())
    val uiState: StateFlow<AddressUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun refresh() {
        if (!_uiState.value.isRefreshing) load(refresh = true)
    }

    fun retry() = load()

    fun showAddForm() = _uiState.update { it.copy(showAddForm = true, error = null, formError = null) }

    fun hideAddForm() = _uiState.update {
        it.copy(showAddForm = false, label = "", addressLine = "", pincode = "", location = null, formError = null)
    }

    fun updateLabel(value: String) = _uiState.update {
        it.copy(label = value.take(60), formError = null)
    }

    fun updateAddressLine(value: String) = _uiState.update {
        it.copy(addressLine = value.take(300), formError = null)
    }

    fun updatePincode(value: String) = _uiState.update {
        it.copy(pincode = value.filter(Char::isDigit).take(6), formError = null)
    }

    fun locationPermissionDenied() = _uiState.update {
        it.copy(formError = AddressFormError.LOCATION_PERMISSION)
    }

    fun captureLocation() {
        if (_uiState.value.isLocating) return
        viewModelScope.launch(dispatcher) {
            _uiState.update { it.copy(isLocating = true, formError = null) }
            runCatching { locationDataSource.currentLocation() }
                .onSuccess { location ->
                    val error = if (location.accuracyMeters > MAX_ACCURACY_METERS) {
                        AddressFormError.LOCATION_INACCURATE
                    } else {
                        null
                    }
                    _uiState.update { it.copy(isLocating = false, location = location, formError = error) }
                }
                .onFailure { failure ->
                    _uiState.update { it.copy(isLocating = false, formError = failure.locationError()) }
                }
        }
    }

    fun saveAddress() {
        val state = _uiState.value
        val validationError = state.validationError()
        if (validationError != null) {
            _uiState.update { it.copy(formError = validationError) }
            return
        }
        if (state.isSaving) return
        viewModelScope.launch(dispatcher) {
            _uiState.update { it.copy(isSaving = true, error = null, formError = null) }
            val request = NewAddress(
                label = state.label.trim(),
                addressLine = state.addressLine.trim(),
                pincode = state.pincode,
                location = requireNotNull(state.location),
            )
            runCatching { addressRepository.create(request) }
                .onSuccess { address ->
                    _uiState.update {
                        it.copy(
                            addresses = listOf(address) + it.addresses.filterNot { saved -> saved.id == address.id },
                            isSaving = false,
                            showAddForm = false,
                            label = "",
                            addressLine = "",
                            pincode = "",
                            location = null,
                        )
                    }
                }
                .onFailure { failure ->
                    _uiState.update { it.copy(isSaving = false, error = failure.toUiDataError()) }
                }
        }
    }

    fun deleteAddress(addressId: String) {
        if (_uiState.value.deletingAddressId != null) return
        viewModelScope.launch(dispatcher) {
            _uiState.update { it.copy(deletingAddressId = addressId, error = null) }
            runCatching { addressRepository.delete(addressId) }
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            addresses = it.addresses.filterNot { address -> address.id == addressId },
                            deletingAddressId = null,
                        )
                    }
                }
                .onFailure { failure ->
                    _uiState.update {
                        it.copy(deletingAddressId = null, error = failure.toUiDataError())
                    }
                }
        }
    }

    private fun load(refresh: Boolean = false) {
        viewModelScope.launch(dispatcher) {
            _uiState.update {
                it.copy(isLoading = !refresh && it.addresses.isEmpty(), isRefreshing = refresh, error = null)
            }
            runCatching { addressRepository.addresses() }
                .onSuccess { addresses ->
                    _uiState.update {
                        it.copy(addresses = addresses, isLoading = false, isRefreshing = false, error = null)
                    }
                }
                .onFailure { failure ->
                    _uiState.update {
                        it.copy(isLoading = false, isRefreshing = false, error = failure.toUiDataError())
                    }
                }
        }
    }

    private fun AddressUiState.validationError(): AddressFormError? = when {
        label.trim().isEmpty() -> AddressFormError.INVALID_LABEL
        addressLine.trim().length < 5 -> AddressFormError.INVALID_ADDRESS
        !PINCODE.matches(pincode) -> AddressFormError.INVALID_PINCODE
        location == null -> AddressFormError.LOCATION_REQUIRED
        location.accuracyMeters > MAX_ACCURACY_METERS -> AddressFormError.LOCATION_INACCURATE
        else -> null
    }

    private fun Throwable.locationError(): AddressFormError = when ((this as? LocationFailure)?.reason) {
        LocationFailureReason.PERMISSION_REQUIRED -> AddressFormError.LOCATION_PERMISSION
        LocationFailureReason.TIMEOUT -> AddressFormError.LOCATION_TIMEOUT
        LocationFailureReason.UNAVAILABLE, null -> AddressFormError.LOCATION_UNAVAILABLE
    }

    private companion object {
        val PINCODE = Regex("400[0-9]{3}")
        const val MAX_ACCURACY_METERS = 100.0
    }
}
