package com.snabbit.prototype.mobile.ui.emailauth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snabbit.prototype.mobile.domain.auth.PasswordCredentialSaver
import com.snabbit.prototype.mobile.domain.auth.PendingPasswordCredentialStore
import com.snabbit.prototype.mobile.domain.auth.ResendVerificationUseCase
import com.snabbit.prototype.mobile.domain.auth.VerifyEmailUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

data class VerifyEmailUiState(
    val email: String,
    val code: String = "",
    val isSubmitting: Boolean = false,
    val resendSeconds: Long = DEFAULT_RESEND_SECONDS,
    val error: EmailAuthError? = null,
) {
    companion object { const val DEFAULT_RESEND_SECONDS = 60L }
}

sealed interface VerifyEmailUiEvent { data object Authenticated : VerifyEmailUiEvent }

class VerifyEmailViewModel(
    email: String,
    private val verify: VerifyEmailUseCase,
    private val resend: ResendVerificationUseCase,
    private val pendingCredential: PendingPasswordCredentialStore,
    private val credentialSaver: PasswordCredentialSaver,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main,
    private val rememberMe: Boolean = true,
) : ViewModel() {
    private val _uiState = MutableStateFlow(VerifyEmailUiState(email))
    val uiState: StateFlow<VerifyEmailUiState> = _uiState.asStateFlow()
    private val _events = MutableSharedFlow<VerifyEmailUiEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<VerifyEmailUiEvent> = _events.asSharedFlow()
    private var countdown: Job? = null

    init { startCountdown(VerifyEmailUiState.DEFAULT_RESEND_SECONDS) }

    fun updateCode(value: String) {
        val next = value.filter(Char::isDigit).take(6)
        _uiState.update { it.copy(code = next, error = null) }
        if (next.length == 6) submit()
    }

    fun submit() {
        val state = _uiState.value
        if (state.isSubmitting || state.code.length != 6) return
        _uiState.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch(dispatcher) {
            runCatching { verify(state.email, state.code, rememberMe) }
                .onSuccess {
                    val credential = pendingCredential.consume(state.email)
                    _uiState.update { it.copy(isSubmitting = false, code = "") }
                    _events.emit(VerifyEmailUiEvent.Authenticated)
                    credential?.let { (email, password) ->
                        withContext(NonCancellable) {
                            withTimeoutOrNull(CREDENTIAL_SAVE_TIMEOUT_MILLIS) { credentialSaver.save(email, password) }
                        }
                    }
                }
                .onFailure { failure ->
                    pendingCredential.clear()
                    val error = failure.toEmailAuthError()
                    _uiState.update { it.copy(isSubmitting = false, code = "", error = error) }
                    error.retryAfterSeconds?.let(::startCountdown)
                }
        }
    }

    fun resend() {
        val state = _uiState.value
        if (state.isSubmitting || state.resendSeconds > 0) return
        _uiState.update { it.copy(isSubmitting = true, error = null, code = "") }
        viewModelScope.launch(dispatcher) {
            runCatching { resend(state.email) }
                .onSuccess {
                    _uiState.update { it.copy(isSubmitting = false) }
                    startCountdown(VerifyEmailUiState.DEFAULT_RESEND_SECONDS)
                }
                .onFailure { failure ->
                    val error = failure.toEmailAuthError()
                    _uiState.update { it.copy(isSubmitting = false, error = error) }
                    startCountdown(error.retryAfterSeconds ?: VerifyEmailUiState.DEFAULT_RESEND_SECONDS)
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
        pendingCredential.clear()
        _uiState.update { it.copy(code = "") }
        super.onCleared()
    }

    private companion object { const val CREDENTIAL_SAVE_TIMEOUT_MILLIS = 30_000L }
}

