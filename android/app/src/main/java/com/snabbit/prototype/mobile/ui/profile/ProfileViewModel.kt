package com.snabbit.prototype.mobile.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snabbit.prototype.mobile.domain.profile.ProfileRepository
import com.snabbit.prototype.mobile.domain.profile.ProfileSummary
import com.snabbit.prototype.mobile.domain.auth.GoogleIdTokenProvider
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
    val phoneDigits: String = "",
    val phoneError: Boolean = false,
    val isSavingPhone: Boolean = false,
)

class ProfileViewModel(
    private val repository: ProfileRepository,
    private val credentialProvider: GoogleIdTokenProvider,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main,
) : ViewModel() {
    constructor(repository: ProfileRepository, dispatcher: CoroutineDispatcher) : this(
        repository,
        object : GoogleIdTokenProvider {
            override suspend fun requestIdToken() = error("unused")
            override suspend fun clearCredentialState() = Unit
        },
        dispatcher,
    )
    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()
    init { load(false) }
    fun refresh() = load(true)
    fun retry() = load(false)
    fun updatePhone(value: String) {
        _uiState.update { it.copy(phoneDigits = value.filter(Char::isDigit).take(10), phoneError = false) }
    }
    fun savePhone() {
        val phone = _uiState.value.phoneDigits
        if (!Regex("[6-9][0-9]{9}").matches(phone)) {
            _uiState.update { it.copy(phoneError = true) }
            return
        }
        viewModelScope.launch(dispatcher) {
            _uiState.update { it.copy(isSavingPhone = true, phoneError = false) }
            runCatching { repository.savePhone("+91$phone") }
                .onSuccess { normalized ->
                    _uiState.update { state ->
                        state.copy(
                            isSavingPhone = false,
                            phoneDigits = normalized.removePrefix("+91"),
                            profile = state.profile?.copy(phoneNumber = normalized),
                        )
                    }
                }
                .onFailure { failure ->
                    _uiState.update { it.copy(isSavingPhone = false, error = failure.toUiDataError()) }
                }
        }
    }
    fun signOut(allDevices: Boolean = false) = viewModelScope.launch(dispatcher) {
        _uiState.update { it.copy(isSigningOut = true, error = null) }
        runCatching {
            repository.signOut(allDevices)
            credentialProvider.clearCredentialState()
        }
            .onFailure { failure -> _uiState.update { it.copy(isSigningOut = false, error = failure.toUiDataError()) } }
    }

    private fun load(refreshing: Boolean) = viewModelScope.launch(dispatcher) {
        _uiState.update { it.copy(isLoading = !refreshing && it.profile == null, isRefreshing = refreshing, error = null) }
        runCatching { repository.summary() }
            .onSuccess { profile -> _uiState.update {
                it.copy(
                    isLoading = false,
                    isRefreshing = false,
                    profile = profile,
                    phoneDigits = profile.phoneNumber?.removePrefix("+91").orEmpty(),
                )
            } }
            .onFailure { failure -> _uiState.update { it.copy(isLoading = false, isRefreshing = false, error = failure.toUiDataError()) } }
    }
}
