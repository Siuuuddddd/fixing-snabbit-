package com.snabbit.prototype.mobile.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snabbit.prototype.mobile.domain.auth.AuthFailure
import com.snabbit.prototype.mobile.domain.auth.AuthFailureReason
import com.snabbit.prototype.mobile.domain.auth.GoogleSignInResult
import com.snabbit.prototype.mobile.domain.auth.PendingWorkerSelection
import com.snabbit.prototype.mobile.domain.auth.PendingWorkerSelectionStore
import com.snabbit.prototype.mobile.domain.auth.SignInWithGoogleUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val isLoading: Boolean = false,
    val isConfigured: Boolean = true,
    val error: LoginError? = null,
)

enum class LoginError {
    NOT_CONFIGURED,
    NO_GOOGLE_ACCOUNT,
    OFFLINE,
    PLAY_SERVICES_UNAVAILABLE,
    GOOGLE_CONFIGURATION,
    INVALID_GOOGLE_TOKEN,
    EMAIL_NOT_VERIFIED,
    WORKER_NOT_REGISTERED,
    ACCOUNT_DISABLED,
    ACCOUNT_LINK_CONFLICT,
    RATE_LIMITED,
    UNKNOWN,
}

sealed interface LoginUiEvent {
    data object Authenticated : LoginUiEvent
    data object OpenWorkerPicker : LoginUiEvent
    data object AddGoogleAccount : LoginUiEvent
}

class LoginViewModel(
    private val signIn: SignInWithGoogleUseCase,
    private val pendingWorkerSelection: PendingWorkerSelectionStore,
    isConfigured: Boolean,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        LoginUiState(
            isConfigured = isConfigured,
            error = if (isConfigured) null else LoginError.NOT_CONFIGURED,
        ),
    )
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()
    private val _events = MutableSharedFlow<LoginUiEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<LoginUiEvent> = _events.asSharedFlow()

    fun continueWithGoogle() {
        val state = _uiState.value
        if (state.isLoading || !state.isConfigured) return
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch(dispatcher) {
            runCatching { signIn() }
                .onSuccess { result ->
                    _uiState.update { it.copy(isLoading = false) }
                    when (result) {
                        is GoogleSignInResult.Authenticated -> _events.emit(LoginUiEvent.Authenticated)
                        is GoogleSignInResult.WorkerSelectionRequired -> {
                            pendingWorkerSelection.save(
                                PendingWorkerSelection(result.selectionToken, result.expiresAtEpochSeconds, result.workers),
                            )
                            _events.emit(LoginUiEvent.OpenWorkerPicker)
                        }
                    }
                }
                .onFailure(::showFailure)
        }
    }

    fun addGoogleAccount() {
        _events.tryEmit(LoginUiEvent.AddGoogleAccount)
    }

    fun retry() {
        _uiState.update { it.copy(error = if (it.isConfigured) null else LoginError.NOT_CONFIGURED) }
        continueWithGoogle()
    }

    private fun showFailure(failure: Throwable) {
        val reason = (failure as? AuthFailure)?.reason
        if (reason == AuthFailureReason.CANCELLED) {
            _uiState.update { it.copy(isLoading = false, error = null) }
            return
        }
        val error = when (reason) {
            AuthFailureReason.NOT_CONFIGURED -> LoginError.NOT_CONFIGURED
            AuthFailureReason.NO_GOOGLE_ACCOUNT -> LoginError.NO_GOOGLE_ACCOUNT
            AuthFailureReason.OFFLINE -> LoginError.OFFLINE
            AuthFailureReason.PLAY_SERVICES_UNAVAILABLE -> LoginError.PLAY_SERVICES_UNAVAILABLE
            AuthFailureReason.GOOGLE_CONFIGURATION -> LoginError.GOOGLE_CONFIGURATION
            AuthFailureReason.INVALID_GOOGLE_TOKEN -> LoginError.INVALID_GOOGLE_TOKEN
            AuthFailureReason.EMAIL_NOT_VERIFIED -> LoginError.EMAIL_NOT_VERIFIED
            AuthFailureReason.WORKER_NOT_REGISTERED -> LoginError.WORKER_NOT_REGISTERED
            AuthFailureReason.ACCOUNT_DISABLED -> LoginError.ACCOUNT_DISABLED
            AuthFailureReason.ACCOUNT_LINK_CONFLICT -> LoginError.ACCOUNT_LINK_CONFLICT
            AuthFailureReason.RATE_LIMITED -> LoginError.RATE_LIMITED
            else -> LoginError.UNKNOWN
        }
        _uiState.update { it.copy(isLoading = false, error = error) }
    }
}
