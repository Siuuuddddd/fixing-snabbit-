package com.snabbit.prototype.mobile.ui.emailauth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snabbit.prototype.mobile.domain.auth.ChangePasswordUseCase
import com.snabbit.prototype.mobile.domain.auth.PasswordRules
import com.snabbit.prototype.mobile.domain.auth.PasswordRule
import com.snabbit.prototype.mobile.domain.auth.PasswordValidation
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

data class ChangePasswordUiState(
    val currentPassword: String = "",
    val newPassword: String = "",
    val currentVisible: Boolean = false,
    val newVisible: Boolean = false,
    val passwordValidation: PasswordValidation = PasswordValidation(PasswordRule.entries.toSet()),
    val currentError: Boolean = false,
    val newError: Boolean = false,
    val isSubmitting: Boolean = false,
    val error: EmailAuthError? = null,
)

sealed interface ChangePasswordUiEvent { data object PasswordChanged : ChangePasswordUiEvent }

class ChangePasswordViewModel(
    private val changePassword: ChangePasswordUseCase,
    private val passwordRules: PasswordRules,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ChangePasswordUiState())
    val uiState: StateFlow<ChangePasswordUiState> = _uiState.asStateFlow()
    private val _events = MutableSharedFlow<ChangePasswordUiEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<ChangePasswordUiEvent> = _events.asSharedFlow()

    fun updateCurrent(value: String) = _uiState.update { it.copy(currentPassword = value.take(128), currentError = false, error = null) }
    fun updateNew(value: String) = _uiState.update { state ->
        val next = value.take(128)
        state.copy(newPassword = next, passwordValidation = passwordRules.validate(next), newError = false, error = null)
    }
    fun toggleCurrentVisibility() = _uiState.update { it.copy(currentVisible = !it.currentVisible) }
    fun toggleNewVisibility() = _uiState.update { it.copy(newVisible = !it.newVisible) }
    fun validateOnBlur() = _uiState.update { it.copy(currentError = it.currentPassword.isBlank(), newError = !it.passwordValidation.isValid) }
    fun submit() {
        val state = _uiState.value
        val currentInvalid = state.currentPassword.isBlank()
        val nextInvalid = !state.passwordValidation.isValid
        if (state.isSubmitting || currentInvalid || nextInvalid) {
            _uiState.update { it.copy(currentError = currentInvalid, newError = nextInvalid) }
            return
        }
        _uiState.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch(dispatcher) {
            runCatching { changePassword(state.currentPassword, state.newPassword) }
                .onSuccess {
                    _uiState.update { it.copy(isSubmitting = false, currentPassword = "", newPassword = "") }
                    _events.emit(ChangePasswordUiEvent.PasswordChanged)
                }
                .onFailure { failure ->
                    val error = failure.toEmailAuthError()
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            currentPassword = "",
                            newPassword = "",
                            currentError = error.fields.keys.any { field -> field.equals("currentPassword", ignoreCase = true) } ||
                                error.type == EmailAuthUiError.INVALID_CREDENTIALS,
                            newError = error.fields.keys.any { field -> field.equals("newPassword", ignoreCase = true) } ||
                                error.type == EmailAuthUiError.WEAK_PASSWORD,
                            error = error,
                        )
                    }
                }
        }
    }

    override fun onCleared() {
        _uiState.update { it.copy(currentPassword = "", newPassword = "") }
        super.onCleared()
    }
}

