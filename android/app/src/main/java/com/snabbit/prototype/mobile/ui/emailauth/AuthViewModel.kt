package com.snabbit.prototype.mobile.ui.emailauth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snabbit.prototype.mobile.domain.auth.AuthFailure
import com.snabbit.prototype.mobile.domain.auth.AuthFailureReason
import com.snabbit.prototype.mobile.domain.auth.AuthNotice
import com.snabbit.prototype.mobile.domain.auth.AuthNoticeStore
import com.snabbit.prototype.mobile.domain.auth.EmailValidator
import com.snabbit.prototype.mobile.domain.auth.LoginWithPasswordUseCase
import com.snabbit.prototype.mobile.domain.auth.NameValidator
import com.snabbit.prototype.mobile.domain.auth.PasswordCredentialSaver
import com.snabbit.prototype.mobile.domain.auth.PasswordRule
import com.snabbit.prototype.mobile.domain.auth.PasswordRules
import com.snabbit.prototype.mobile.domain.auth.PasswordValidation
import com.snabbit.prototype.mobile.domain.auth.PendingPasswordCredentialStore
import com.snabbit.prototype.mobile.domain.auth.RegisterUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
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

enum class AuthTab { LOGIN, SIGN_UP }

data class AuthUiState(
    val tab: AuthTab = AuthTab.LOGIN,
    val loginEmail: String = "",
    val loginPassword: String = "",
    val signUpName: String = "",
    val signUpEmail: String = "",
    val signUpPassword: String = "",
    val termsAccepted: Boolean = false,
    val passwordVisible: Boolean = false,
    val isSubmitting: Boolean = false,
    val emailError: Boolean = false,
    val nameError: Boolean = false,
    val passwordError: Boolean = false,
    val termsError: Boolean = false,
    val passwordValidation: PasswordValidation = PasswordValidation(PasswordRule.entries.toSet()),
    val error: EmailAuthError? = null,
    val notice: AuthNotice? = null,
)

sealed interface AuthUiEvent {
    data object Authenticated : AuthUiEvent
    data class VerifyEmail(val email: String) : AuthUiEvent
}

class AuthViewModel(
    private val login: LoginWithPasswordUseCase,
    private val register: RegisterUseCase,
    private val passwordRules: PasswordRules,
    private val pendingCredential: PendingPasswordCredentialStore,
    private val credentialSaver: PasswordCredentialSaver,
    noticeStore: AuthNoticeStore,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AuthUiState(notice = noticeStore.consume()))
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()
    private val _events = MutableSharedFlow<AuthUiEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<AuthUiEvent> = _events.asSharedFlow()

    fun selectTab(tab: AuthTab) {
        if (_uiState.value.isSubmitting || tab == _uiState.value.tab) return
        _uiState.update { it.copy(tab = tab, error = null, emailError = false, nameError = false, passwordError = false, termsError = false, passwordVisible = false) }
    }
    fun updateLoginEmail(value: String) = _uiState.update { it.copy(loginEmail = value, emailError = false, error = null) }
    fun updateLoginPassword(value: String) = _uiState.update { it.copy(loginPassword = value.take(128), passwordError = false, error = null) }
    fun updateSignUpName(value: String) = _uiState.update { state ->
        val next = value.take(120)
        state.copy(signUpName = next, nameError = false, passwordValidation = passwordRules.validate(state.signUpPassword, state.signUpEmail, next), error = null)
    }
    fun updateSignUpEmail(value: String) = _uiState.update { state ->
        val next = value.take(254)
        state.copy(signUpEmail = next, emailError = false, passwordValidation = passwordRules.validate(state.signUpPassword, next, state.signUpName), error = null)
    }
    fun updateSignUpPassword(value: String) = _uiState.update { state ->
        val next = value.take(128)
        state.copy(signUpPassword = next, passwordError = false, passwordValidation = passwordRules.validate(next, state.signUpEmail, state.signUpName), error = null)
    }
    fun setTermsAccepted(value: Boolean) = _uiState.update { it.copy(termsAccepted = value, termsError = false, error = null) }
    fun togglePasswordVisibility() = _uiState.update { it.copy(passwordVisible = !it.passwordVisible) }
    fun validateEmailOnBlur() = _uiState.update { state ->
        val email = if (state.tab == AuthTab.LOGIN) state.loginEmail else state.signUpEmail
        state.copy(emailError = email.isNotBlank() && !EmailValidator.isValid(email))
    }
    fun validateNameOnBlur() = _uiState.update { it.copy(nameError = it.signUpName.isNotBlank() && !NameValidator.isValid(it.signUpName)) }
    fun validatePasswordOnBlur() = _uiState.update { state ->
        val invalid = if (state.tab == AuthTab.LOGIN) state.loginPassword.isBlank() else !state.passwordValidation.isValid
        state.copy(passwordError = invalid)
    }
    fun dismissNotice() = _uiState.update { it.copy(notice = null) }

    fun submit() {
        val state = _uiState.value
        if (state.isSubmitting) return
        if (state.tab == AuthTab.LOGIN) submitLogin(state) else submitRegistration(state)
    }

    private fun submitLogin(state: AuthUiState) {
        val emailInvalid = !EmailValidator.isValid(state.loginEmail)
        val passwordInvalid = state.loginPassword.isBlank()
        if (emailInvalid || passwordInvalid) {
            _uiState.update { it.copy(emailError = emailInvalid, passwordError = passwordInvalid) }
            return
        }
        _uiState.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch(dispatcher) {
            runCatching { login(EmailValidator.normalize(state.loginEmail), state.loginPassword) }
                .onSuccess {
                    val email = EmailValidator.normalize(state.loginEmail)
                    val password = state.loginPassword
                    _uiState.update { current -> current.copy(isSubmitting = false, loginPassword = "") }
                    _events.emit(AuthUiEvent.Authenticated)
                    saveCredentialAfterNavigation(email, password)
                }
                .onFailure { failure ->
                    if ((failure as? AuthFailure)?.reason == AuthFailureReason.EMAIL_NOT_VERIFIED) {
                        pendingCredential.clear()
                        _uiState.update { it.copy(isSubmitting = false, loginPassword = "") }
                        _events.emit(AuthUiEvent.VerifyEmail(EmailValidator.normalize(state.loginEmail)))
                    } else {
                        val error = failure.toEmailAuthError()
                        _uiState.update {
                            it.copy(
                                isSubmitting = false,
                                loginPassword = "",
                                emailError = error.hasField("email"),
                                passwordError = error.hasField("password"),
                                error = error,
                            )
                        }
                    }
                }
        }
    }

    private fun submitRegistration(state: AuthUiState) {
        val emailInvalid = !EmailValidator.isValid(state.signUpEmail)
        val nameInvalid = !NameValidator.isValid(state.signUpName)
        val passwordInvalid = !state.passwordValidation.isValid
        if (emailInvalid || nameInvalid || passwordInvalid || !state.termsAccepted) {
            _uiState.update { it.copy(emailError = emailInvalid, nameError = nameInvalid, passwordError = passwordInvalid, termsError = !state.termsAccepted) }
            return
        }
        _uiState.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch(dispatcher) {
            val email = EmailValidator.normalize(state.signUpEmail)
            runCatching { register(NameValidator.normalize(state.signUpName), email, state.signUpPassword) }
                .onSuccess {
                    pendingCredential.save(email, state.signUpPassword)
                    _uiState.update { it.copy(isSubmitting = false, signUpPassword = "") }
                    _events.emit(AuthUiEvent.VerifyEmail(email))
                }
                .onFailure { failure ->
                    val error = failure.toEmailAuthError()
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            signUpPassword = "",
                            nameError = error.hasField("name"),
                            emailError = error.hasField("email"),
                            passwordError = error.hasField("password") || error.type == EmailAuthUiError.WEAK_PASSWORD,
                            termsError = error.hasField("termsAccepted") || error.type == EmailAuthUiError.TERMS_NOT_ACCEPTED,
                            error = error,
                        )
                    }
                }
        }
    }

    override fun onCleared() {
        _uiState.update { it.copy(loginPassword = "", signUpPassword = "") }
        super.onCleared()
    }

    private suspend fun saveCredentialAfterNavigation(email: String, password: String) {
        withContext(NonCancellable) {
            withTimeoutOrNull(CREDENTIAL_SAVE_TIMEOUT_MILLIS) { credentialSaver.save(email, password) }
        }
    }

    private companion object { const val CREDENTIAL_SAVE_TIMEOUT_MILLIS = 30_000L }
}

private fun EmailAuthError.hasField(name: String): Boolean = fields.keys.any { it.equals(name, ignoreCase = true) }

