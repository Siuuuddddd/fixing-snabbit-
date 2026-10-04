package com.snabbit.prototype.mobile.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snabbit.prototype.mobile.domain.catalog.CatalogRepository
import com.snabbit.prototype.mobile.domain.catalog.ServiceOffering
import com.snabbit.prototype.mobile.ui.common.UiDataError
import com.snabbit.prototype.mobile.ui.common.toUiDataError
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val services: List<ServiceOffering> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: UiDataError? = null,
)

class HomeViewModel(
    private val catalogRepository: CatalogRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun retry() = load()

    fun refresh() {
        if (!_uiState.value.isRefreshing) load(refresh = true)
    }

    private fun load(refresh: Boolean = false) {
        viewModelScope.launch(dispatcher) {
            _uiState.update {
                it.copy(isLoading = !refresh && it.services.isEmpty(), isRefreshing = refresh, error = null)
            }
            runCatching { catalogRepository.services() }
                .onSuccess { services ->
                    _uiState.update {
                        it.copy(services = services, isLoading = false, isRefreshing = false, error = null)
                    }
                }
                .onFailure { failure ->
                    _uiState.update {
                        it.copy(isLoading = false, isRefreshing = false, error = failure.toUiDataError())
                    }
                }
        }
    }
}
