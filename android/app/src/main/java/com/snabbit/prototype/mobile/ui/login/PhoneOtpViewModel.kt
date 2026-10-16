package com.snabbit.prototype.mobile.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snabbit.prototype.mobile.domain.auth.AuthFailure
import com.snabbit.prototype.mobile.domain.auth.AuthFailureReason
import com.snabbit.prototype.mobile.domain.auth.IndianMobileNumber
import com.snabbit.prototype.mobile.domain.auth.RequestPhoneOtpUseCase
import com.snabbit.prototype.mobile.domain.auth.VerifyPhoneOtpUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PhoneOtpUiState(
    val phoneNumber: String,
    val code: String = "",
    val resendSeconds: Long,
    val isLoading: Boolean = false,
    val error: PhoneOtpError? = null,
) { val maskedPhone: String get() = IndianMobileNumber.masked(phoneNumber) }

enum class PhoneOtpError { INVALID_OR_EXPIRED, TOO_MANY_ATTEMPTS, RATE_LIMITED, OFFLINE, SERVER, UNKNOWN }
sealed interface PhoneOtpUiEvent { data object Authenticated : PhoneOtpUiEvent }

class PhoneOtpViewModel(
    private val phoneNumber: String,
    private val rememberMe: Boolean,
    initialResendSeconds: Long,
    private val verifyOtp: VerifyPhoneOtpUseCase,
    private val requestOtp: RequestPhoneOtpUseCase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PhoneOtpUiState(phoneNumber, resendSeconds = initialResendSeconds))
    val uiState: StateFlow<PhoneOtpUiState> = _uiState.asStateFlow()
    private val _events = MutableSharedFlow<PhoneOtpUiEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<PhoneOtpUiEvent> = _events.asSharedFlow()
    private var countdown: Job? = null

    init { startCountdown(initialResendSeconds) }

    fun updateCode(value: String) {
        val next = value.filter(Char::isDigit).take(6)
        _uiState.update { it.copy(code = next, error = null) }
        if (next.length == 6) submit()
    }

    fun submit() {
        val state = _uiState.value
        if (state.isLoading || state.code.length != 6) return
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch(dispatcher) {
            runCatching { verifyOtp(phoneNumber, state.code, rememberMe) }
                .onSuccess {
                    _uiState.update { it.copy(isLoading = false, code = "") }
                    _events.emit(PhoneOtpUiEvent.Authenticated)
                }
                .onFailure { failure ->
                    val auth = failure as? AuthFailure
                    _uiState.update { it.copy(isLoading = false, code = "", error = auth.toPhoneOtpError()) }
                    auth?.retryAfterSeconds?.let(::startCountdown)
                }
        }
    }

    fun resend() {
        val state = _uiState.value
        if (state.isLoading || state.resendSeconds > 0) return
        _uiState.update { it.copy(isLoading = true, code = "", error = null) }
        viewModelScope.launch(dispatcher) {
            runCatching { requestOtp(phoneNumber) }
                .onSuccess { seconds ->
                    _uiState.update { it.copy(isLoading = false) }
                    startCountdown(seconds)
                }
                .onFailure { failure ->
                    val auth = failure as? AuthFailure
                    _uiState.update { it.copy(isLoading = false, error = auth.toPhoneOtpError()) }
                    startCountdown(auth?.retryAfterSeconds ?: 30)
                }
        }
    }

    private fun startCountdown(seconds: Long) {
        countdown?.cancel()
        _uiState.update { it.copy(resendSeconds = seconds.coerceAtLeast(0)) }
        countdown = viewModelScope.launch(dispatcher) {
            while (_uiState.value.resendSeconds > 0) {
                delay(1_000)
                _uiState.update { it.copy(resendSeconds = (it.resendSeconds - 1).coerceAtLeast(0)) }
            }
        }
    }

    override fun onCleared() {
        countdown?.cancel()
        _uiState.update { it.copy(code = "") }
        super.onCleared()
    }
}

private fun AuthFailure?.toPhoneOtpError(): PhoneOtpError = when (this?.reason) {
    AuthFailureReason.INVALID_OR_EXPIRED_CODE -> PhoneOtpError.INVALID_OR_EXPIRED
    AuthFailureReason.TOO_MANY_ATTEMPTS -> PhoneOtpError.TOO_MANY_ATTEMPTS
    AuthFailureReason.RATE_LIMITED -> PhoneOtpError.RATE_LIMITED
    AuthFailureReason.OFFLINE -> PhoneOtpError.OFFLINE
    AuthFailureReason.SERVER -> PhoneOtpError.SERVER
    else -> PhoneOtpError.UNKNOWN
}
