package com.snabbit.prototype.mobile.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snabbit.prototype.mobile.domain.profile.ProfileRepository
import com.snabbit.prototype.mobile.domain.profile.ProfileSummary
import com.snabbit.prototype.mobile.ui.common.UiDataError
import com.snabbit.prototype.mobile.ui.common.toUiDataError
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isSigningOut: Boolean = false,
    val profile: ProfileSummary? = null,
    val error: UiDataError? = null,
)

class ProfileViewModel(
    private val repository: ProfileRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()
    init { load(false) }
    fun refresh() = load(true)
    fun retry() = load(false)
    fun signOut() = viewModelScope.launch(dispatcher) {
        _uiState.update { it.copy(isSigningOut = true, error = null) }
        runCatching { repository.signOut() }
            .onFailure { failure -> _uiState.update { it.copy(isSigningOut = false, error = failure.toUiDataError()) } }
    }

    private fun load(refreshing: Boolean) = viewModelScope.launch(dispatcher) {
        _uiState.update { it.copy(isLoading = !refreshing && it.profile == null, isRefreshing = refreshing, error = null) }
        runCatching { repository.summary() }
            .onSuccess { profile -> _uiState.update { it.copy(isLoading = false, isRefreshing = false, profile = profile) } }
            .onFailure { failure -> _uiState.update { it.copy(isLoading = false, isRefreshing = false, error = failure.toUiDataError()) } }
    }
}
