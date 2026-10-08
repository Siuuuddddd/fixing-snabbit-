package com.snabbit.prototype.mobile.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snabbit.prototype.mobile.domain.home.FavoriteExpertsPreview
import com.snabbit.prototype.mobile.domain.home.GetFavoritesUseCase
import com.snabbit.prototype.mobile.domain.home.GetHabitPackPromoUseCase
import com.snabbit.prototype.mobile.domain.home.GetHomeServicesUseCase
import com.snabbit.prototype.mobile.domain.home.GetSelectedAddressUseCase
import com.snabbit.prototype.mobile.domain.home.HabitPackPromo
import com.snabbit.prototype.mobile.domain.home.HomeServiceTile
import com.snabbit.prototype.mobile.domain.home.SelectedHomeAddress
import com.snabbit.prototype.mobile.ui.common.UiDataError
import com.snabbit.prototype.mobile.ui.common.toUiDataError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

enum class HomeBookingMode { SCHEDULED, INSTANT }

sealed interface HomeUiEvent {
    data object Refresh : HomeUiEvent
    data object Retry : HomeUiEvent
    data object AddressSelected : HomeUiEvent
    data object WalletSelected : HomeUiEvent
    data object RewardsSelected : HomeUiEvent
    data object ProfileSelected : HomeUiEvent
    data object ScheduleSelected : HomeUiEvent
    data object InstantSelected : HomeUiEvent
    data object PromoSelected : HomeUiEvent
    data class ServiceSelected(val serviceId: String) : HomeUiEvent
}

data class HomeUiState(
    val services: List<HomeServiceTile> = emptyList(),
    val selectedAddress: SelectedHomeAddress? = null,
    val favorites: FavoriteExpertsPreview? = null,
    val promo: HabitPackPromo? = null,
    val preferredBookingMode: HomeBookingMode? = null,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: UiDataError? = null,
)

class HomeViewModel(
    private val getHomeServices: GetHomeServicesUseCase,
    private val getSelectedAddress: GetSelectedAddressUseCase,
    private val getFavorites: GetFavoritesUseCase,
    private val getHabitPackPromo: GetHabitPackPromoUseCase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun onEvent(event: HomeUiEvent) {
        when (event) {
            HomeUiEvent.Refresh -> if (!_uiState.value.isRefreshing) load(refresh = true)
            HomeUiEvent.Retry -> load()
            HomeUiEvent.ScheduleSelected -> _uiState.update {
                it.copy(preferredBookingMode = HomeBookingMode.SCHEDULED)
            }
            HomeUiEvent.InstantSelected -> _uiState.update {
                it.copy(preferredBookingMode = HomeBookingMode.INSTANT)
            }
            HomeUiEvent.AddressSelected,
            HomeUiEvent.WalletSelected,
            HomeUiEvent.RewardsSelected,
            HomeUiEvent.ProfileSelected,
            HomeUiEvent.PromoSelected,
            is HomeUiEvent.ServiceSelected,
            -> Unit
        }
    }

    private fun load(refresh: Boolean = false) {
        viewModelScope.launch(dispatcher) {
            _uiState.update {
                it.copy(isLoading = !refresh && it.services.isEmpty(), isRefreshing = refresh, error = null)
            }
            try {
                val loaded = supervisorScope {
                    val services = async { getHomeServices() }
                    val address = async { getSelectedAddress() }
                    val favorites = async { getFavorites() }
                    val promo = async { getHabitPackPromo() }
                    HomeLoadResult(services.await(), address.await(), favorites.await(), promo.await())
                }
                _uiState.update {
                    it.copy(
                        services = loaded.services,
                        selectedAddress = loaded.address,
                        favorites = loaded.favorites,
                        promo = loaded.promo,
                        isLoading = false,
                        isRefreshing = false,
                        error = null,
                    )
                }
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Throwable) {
                _uiState.update {
                    it.copy(isLoading = false, isRefreshing = false, error = failure.toUiDataError())
                }
            }
        }
    }
}

private data class HomeLoadResult(
    val services: List<HomeServiceTile>,
    val address: SelectedHomeAddress?,
    val favorites: FavoriteExpertsPreview,
    val promo: HabitPackPromo,
)
