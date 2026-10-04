package com.snabbit.prototype.mobile.ui.service

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snabbit.prototype.mobile.domain.catalog.CatalogRepository
import com.snabbit.prototype.mobile.domain.catalog.ServiceOffering
import com.snabbit.prototype.mobile.domain.common.DataFailure
import com.snabbit.prototype.mobile.domain.common.DataFailureReason
import com.snabbit.prototype.mobile.ui.common.UiDataError
import com.snabbit.prototype.mobile.ui.common.toUiDataError
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ServiceDetailsUiState(
    val service: ServiceOffering? = null,
    val selectedDurationHours: Int = 2,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: UiDataError? = null,
)

class ServiceDetailsViewModel(
    private val serviceId: String,
    private val catalogRepository: CatalogRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ServiceDetailsUiState())
    val uiState: StateFlow<ServiceDetailsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun selectDuration(hours: Int) {
        if (hours in 1..4) _uiState.update { it.copy(selectedDurationHours = hours) }
    }

    fun retry() = load()

    fun refresh() {
        if (!_uiState.value.isRefreshing) load(refresh = true)
    }

    private fun load(refresh: Boolean = false) {
        viewModelScope.launch(dispatcher) {
            _uiState.update {
                it.copy(isLoading = !refresh && it.service == null, isRefreshing = refresh, error = null)
            }
            runCatching {
                catalogRepository.service(serviceId)
                    ?: throw DataFailure(DataFailureReason.NOT_FOUND)
            }.onSuccess { service ->
                _uiState.update {
                    it.copy(service = service, isLoading = false, isRefreshing = false, error = null)
                }
            }.onFailure { failure ->
                _uiState.update {
                    it.copy(isLoading = false, isRefreshing = false, error = failure.toUiDataError())
                }
            }
        }
    }
}
