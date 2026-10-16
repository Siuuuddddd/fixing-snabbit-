package com.snabbit.prototype.mobile.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snabbit.prototype.mobile.domain.auth.AuthFailure
import com.snabbit.prototype.mobile.domain.auth.AuthFailureReason
import com.snabbit.prototype.mobile.domain.auth.GoogleSignInResult
import com.snabbit.prototype.mobile.domain.auth.IndianMobileNumber
import com.snabbit.prototype.mobile.domain.auth.RequestPhoneOtpUseCase
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

data class CustomerLoginUiState(
    val phoneDigits: String = "",
    val rememberMe: Boolean = true,
    val isLoading: Boolean = false,
    val googleConfigured: Boolean = true,
    val error: CustomerLoginError? = null,
) {
    val canContinue: Boolean get() = IndianMobileNumber.isValid(phoneDigits) && !isLoading
}

enum class CustomerLoginError { INVALID_PHONE, NOT_CONFIGURED, NO_GOOGLE_ACCOUNT, OFFLINE, RATE_LIMITED, SERVER, UNKNOWN }

sealed interface CustomerLoginUiEvent {
    data class OpenOtp(val phoneNumber: String, val rememberMe: Boolean, val resendSeconds: Long) : CustomerLoginUiEvent
    data class OpenEmail(val rememberMe: Boolean) : CustomerLoginUiEvent
    data object Authenticated : CustomerLoginUiEvent
    data object AddGoogleAccount : CustomerLoginUiEvent
}

class CustomerLoginViewModel(
    private val requestPhoneOtp: RequestPhoneOtpUseCase,
    private val signInWithGoogle: SignInWithGoogleUseCase,
    googleConfigured: Boolean,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CustomerLoginUiState(googleConfigured = googleConfigured))
    val uiState: StateFlow<CustomerLoginUiState> = _uiState.asStateFlow()
    private val _events = MutableSharedFlow<CustomerLoginUiEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<CustomerLoginUiEvent> = _events.asSharedFlow()

    fun updatePhone(value: String) = _uiState.update {
        it.copy(phoneDigits = IndianMobileNumber.digits(value), error = null)
    }

    fun setRememberMe(value: Boolean) = _uiState.update { it.copy(rememberMe = value) }

    fun continueWithPhone() {
        val state = _uiState.value
        if (state.isLoading) return
        if (!IndianMobileNumber.isValid(state.phoneDigits)) {
            _uiState.update { it.copy(error = CustomerLoginError.INVALID_PHONE) }
            return
        }
        val phone = IndianMobileNumber.e164(state.phoneDigits)
        launch {
            val resendSeconds = requestPhoneOtp(phone)
            _events.emit(CustomerLoginUiEvent.OpenOtp(phone, state.rememberMe, resendSeconds))
        }
    }

    fun continueWithGoogle() {
        val state = _uiState.value
        if (state.isLoading) return
        if (!state.googleConfigured) {
            _uiState.update { it.copy(error = CustomerLoginError.NOT_CONFIGURED) }
            return
        }
        launch {
            when (signInWithGoogle(rememberMe = state.rememberMe)) {
                is GoogleSignInResult.Authenticated -> _events.emit(CustomerLoginUiEvent.Authenticated)
                is GoogleSignInResult.WorkerSelectionRequired -> error("Customer sign-in cannot require worker selection")
            }
        }
    }

    fun openEmail() = _events.tryEmit(CustomerLoginUiEvent.OpenEmail(_uiState.value.rememberMe))
    fun addGoogleAccount() = _events.tryEmit(CustomerLoginUiEvent.AddGoogleAccount)

    private fun launch(block: suspend () -> Unit) {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch(dispatcher) {
            runCatching { block() }
                .onSuccess { _uiState.update { it.copy(isLoading = false) } }
                .onFailure { failure ->
                    val auth = failure as? AuthFailure
                    if (auth?.reason == AuthFailureReason.CANCELLED) {
                        _uiState.update { it.copy(isLoading = false) }
                    } else {
                        _uiState.update { it.copy(isLoading = false, error = auth.toCustomerLoginError()) }
                    }
                }
        }
    }
}

private fun AuthFailure?.toCustomerLoginError(): CustomerLoginError = when (this?.reason) {
    AuthFailureReason.NOT_CONFIGURED -> CustomerLoginError.NOT_CONFIGURED
    AuthFailureReason.NO_GOOGLE_ACCOUNT -> CustomerLoginError.NO_GOOGLE_ACCOUNT
    AuthFailureReason.OFFLINE -> CustomerLoginError.OFFLINE
    AuthFailureReason.RATE_LIMITED -> CustomerLoginError.RATE_LIMITED
    AuthFailureReason.SERVER -> CustomerLoginError.SERVER
    null -> CustomerLoginError.UNKNOWN
    else -> CustomerLoginError.UNKNOWN
}
