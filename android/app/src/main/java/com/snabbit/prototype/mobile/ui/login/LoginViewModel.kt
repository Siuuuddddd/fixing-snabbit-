package com.snabbit.prototype.mobile.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snabbit.prototype.mobile.domain.auth.AuthFailure
import com.snabbit.prototype.mobile.domain.auth.AuthFailureReason
import com.snabbit.prototype.mobile.domain.auth.AuthRepository
import com.snabbit.prototype.mobile.domain.auth.AuthResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class LoginStep {
    PHONE,
    OTP,
    REGISTRATION,
}

enum class LoginError {
    INVALID_PHONE,
    INVALID_OTP,
    RATE_LIMITED,
    OFFLINE,
    SERVER,
    WORKER_PROFILE_REQUIRED,
    UNKNOWN,
}

data class LoginUiState(
    val step: LoginStep = LoginStep.PHONE,
    val phoneDigits: String = "",
    val otp: String = "",
    val displayName: String = "",
    val registrationToken: String? = null,
    val isLoading: Boolean = false,
    val error: LoginError? = null,
    val resendSeconds: Int = 0,
    val authenticated: Boolean = false,
) {
    val isPhoneValid: Boolean get() = PHONE_REGEX.matches(phoneDigits)
    val isOtpValid: Boolean get() = otp.length == OTP_LENGTH
    val isNameValid: Boolean get() = displayName.trim().length in 2..60

    private companion object {
        val PHONE_REGEX = Regex("[6-9][0-9]{9}")
        const val OTP_LENGTH = 6
    }
}

class LoginViewModel(
    private val authRepository: AuthRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main,
) : ViewModel() {
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()
    private var timerJob: Job? = null

    fun updatePhone(value: String) {
        val digits = value.filter(Char::isDigit).take(PHONE_DIGITS)
        _uiState.update { it.copy(phoneDigits = digits, error = null) }
    }

    fun updateOtp(value: String) {
        val digits = value.filter(Char::isDigit).take(OTP_DIGITS)
        _uiState.update { it.copy(otp = digits, error = null) }
    }

    fun updateDisplayName(value: String) {
        _uiState.update { it.copy(displayName = value.take(60), error = null) }
    }

    fun requestOtp() {
        if (_uiState.value.isLoading) return
        if (!_uiState.value.isPhoneValid) {
            _uiState.update { it.copy(error = LoginError.INVALID_PHONE) }
            return
        }
        requestOtpInternal()
    }

    fun resendOtp() {
        val state = _uiState.value
        if (state.isLoading || state.resendSeconds > 0) return
        requestOtpInternal()
    }

    fun verifyOtp() {
        val state = _uiState.value
        if (state.isLoading) return
        if (!state.isOtpValid) {
            _uiState.update { it.copy(error = LoginError.INVALID_OTP) }
            return
        }
        viewModelScope.launch(dispatcher) {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                authRepository.verifyOtp(fullPhoneNumber(), _uiState.value.otp)
            }.onSuccess { result ->
                timerJob?.cancel()
                when (result) {
                    AuthResult.Authenticated -> _uiState.update { it.copy(isLoading = false, authenticated = true) }
                    is AuthResult.RegistrationRequired -> _uiState.update {
                        it.copy(step = LoginStep.REGISTRATION, registrationToken = result.token, isLoading = false)
                    }
                }
            }.onFailure(::showFailure)
        }
    }

    fun register() {
        val state = _uiState.value
        if (state.isLoading || !state.isNameValid) return
        viewModelScope.launch(dispatcher) {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                authRepository.register(fullPhoneNumber(), state.displayName.trim(), requireNotNull(state.registrationToken))
            }.onSuccess {
                _uiState.update { it.copy(isLoading = false, authenticated = true) }
            }.onFailure(::showFailure)
        }
    }

    fun editPhone() {
        timerJob?.cancel()
        _uiState.update {
            it.copy(step = LoginStep.PHONE, otp = "", resendSeconds = 0, error = null)
        }
    }

    private fun requestOtpInternal() {
        viewModelScope.launch(dispatcher) {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                authRepository.requestOtp(fullPhoneNumber())
            }.onSuccess { challenge ->
                _uiState.update { it.copy(step = LoginStep.OTP, otp = "", isLoading = false) }
                startTimer(challenge.expiresInSeconds.coerceAtMost(RESEND_COOLDOWN_SECONDS.toLong()).toInt())
            }.onFailure(::showFailure)
        }
    }

    private fun startTimer(seconds: Int) {
        timerJob?.cancel()
        timerJob = viewModelScope.launch(dispatcher) {
            _uiState.update { it.copy(resendSeconds = seconds) }
            while (_uiState.value.resendSeconds > 0) {
                delay(ONE_SECOND_MILLIS)
                _uiState.update { state ->
                    state.copy(resendSeconds = (state.resendSeconds - 1).coerceAtLeast(0))
                }
            }
        }
    }

    private fun showFailure(throwable: Throwable) {
        val error = when ((throwable as? AuthFailure)?.reason) {
            AuthFailureReason.INVALID_OR_EXPIRED_OTP -> LoginError.INVALID_OTP
            AuthFailureReason.RATE_LIMITED -> LoginError.RATE_LIMITED
            AuthFailureReason.OFFLINE -> LoginError.OFFLINE
            AuthFailureReason.SERVER -> LoginError.SERVER
            AuthFailureReason.WORKER_PROFILE_REQUIRED -> LoginError.WORKER_PROFILE_REQUIRED
            else -> LoginError.UNKNOWN
        }
        _uiState.update { it.copy(isLoading = false, error = error) }
    }

    private fun fullPhoneNumber(): String = "+91${_uiState.value.phoneDigits}"

    private companion object {
        const val PHONE_DIGITS = 10
        const val OTP_DIGITS = 6
        const val RESEND_COOLDOWN_SECONDS = 30
        const val ONE_SECOND_MILLIS = 1_000L
    }
}
