package com.snabbit.prototype.mobile.ui.emailauth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snabbit.prototype.mobile.domain.auth.AuthNotice
import com.snabbit.prototype.mobile.domain.auth.AuthNoticeStore
import com.snabbit.prototype.mobile.domain.auth.EmailValidator
import com.snabbit.prototype.mobile.domain.auth.ForgotPasswordUseCase
import com.snabbit.prototype.mobile.domain.auth.PasswordRules
import com.snabbit.prototype.mobile.domain.auth.PasswordRule
import com.snabbit.prototype.mobile.domain.auth.PasswordValidation
import com.snabbit.prototype.mobile.domain.auth.ResetPasswordUseCase
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

enum class ForgotPasswordStep { EMAIL, RESET }

data class ForgotPasswordUiState(
    val step: ForgotPasswordStep = ForgotPasswordStep.EMAIL,
    val email: String = "",
    val code: String = "",
    val newPassword: String = "",
    val passwordVisible: Boolean = false,
    val passwordValidation: PasswordValidation = PasswordValidation(PasswordRule.entries.toSet()),
    val emailError: Boolean = false,
    val codeError: Boolean = false,
    val passwordError: Boolean = false,
    val isSubmitting: Boolean = false,
    val error: EmailAuthError? = null,
)

sealed interface ForgotPasswordUiEvent { data object PasswordUpdated : ForgotPasswordUiEvent }

class ForgotPasswordViewModel(
    initialEmail: String,
    private val forgot: ForgotPasswordUseCase,
    private val reset: ResetPasswordUseCase,
    private val passwordRules: PasswordRules,
    private val noticeStore: AuthNoticeStore,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ForgotPasswordUiState(email = initialEmail))
    val uiState: StateFlow<ForgotPasswordUiState> = _uiState.asStateFlow()
    private val _events = MutableSharedFlow<ForgotPasswordUiEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<ForgotPasswordUiEvent> = _events.asSharedFlow()

    fun updateEmail(value: String) = _uiState.update { it.copy(email = value.take(254), emailError = false, error = null) }
    fun updateCode(value: String) = _uiState.update { it.copy(code = value.filter(Char::isDigit).take(6), codeError = false, error = null) }
    fun updatePassword(value: String) = _uiState.update { state ->
        val next = value.take(128)
        state.copy(newPassword = next, passwordValidation = passwordRules.validate(next, state.email), passwordError = false, error = null)
    }
    fun togglePasswordVisibility() = _uiState.update { it.copy(passwordVisible = !it.passwordVisible) }
    fun validateEmailOnBlur() = _uiState.update { it.copy(emailError = it.email.isNotBlank() && !EmailValidator.isValid(it.email)) }
    fun validatePasswordOnBlur() = _uiState.update { it.copy(passwordError = !it.passwordValidation.isValid) }

    fun submit() {
        if (_uiState.value.step == ForgotPasswordStep.EMAIL) requestReset() else resetPassword()
    }

    private fun requestReset() {
        val state = _uiState.value
        if (state.isSubmitting || !EmailValidator.isValid(state.email)) {
            if (!EmailValidator.isValid(state.email)) _uiState.update { it.copy(emailError = true) }
            return
        }
        _uiState.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch(dispatcher) {
            runCatching { forgot(EmailValidator.normalize(state.email)) }
                .onSuccess { _uiState.update { it.copy(step = ForgotPasswordStep.RESET, isSubmitting = false, email = EmailValidator.normalize(it.email)) } }
                .onFailure { failure ->
                    val error = failure.toEmailAuthError()
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            emailError = error.fields.keys.any { field -> field.equals("email", ignoreCase = true) },
                            error = error,
                        )
                    }
                }
        }
    }

    private fun resetPassword() {
        val state = _uiState.value
        val codeInvalid = state.code.length != 6
        val passwordInvalid = !state.passwordValidation.isValid
        if (state.isSubmitting || codeInvalid || passwordInvalid) {
            _uiState.update { it.copy(codeError = codeInvalid, passwordError = passwordInvalid) }
            return
        }
        _uiState.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch(dispatcher) {
            runCatching { reset(state.email, state.code, state.newPassword) }
                .onSuccess {
                    noticeStore.publish(AuthNotice.PASSWORD_UPDATED)
                    _uiState.update { it.copy(isSubmitting = false, code = "", newPassword = "") }
                    _events.emit(ForgotPasswordUiEvent.PasswordUpdated)
                }
                .onFailure { failure ->
                    val error = failure.toEmailAuthError()
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            code = "",
                            newPassword = "",
                            codeError = error.fields.keys.any { field -> field.equals("code", ignoreCase = true) } ||
                                error.type == EmailAuthUiError.INVALID_OR_EXPIRED_CODE,
                            passwordError = error.fields.keys.any { field -> field.equals("newPassword", ignoreCase = true) } ||
                                error.type == EmailAuthUiError.WEAK_PASSWORD,
                            error = error,
                        )
                    }
                }
        }
    }

    override fun onCleared() {
        _uiState.update { it.copy(code = "", newPassword = "") }
        super.onCleared()
    }
}

