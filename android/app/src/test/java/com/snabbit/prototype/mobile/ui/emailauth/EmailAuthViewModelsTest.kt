package com.snabbit.prototype.mobile.ui.emailauth

import com.snabbit.prototype.mobile.data.auth.InMemoryAuthNoticeStore
import com.snabbit.prototype.mobile.data.auth.InMemoryPendingPasswordCredentialStore
import com.snabbit.prototype.mobile.domain.auth.AuthDevice
import com.snabbit.prototype.mobile.domain.auth.AuthFailure
import com.snabbit.prototype.mobile.domain.auth.AuthFailureReason
import com.snabbit.prototype.mobile.domain.auth.AuthNotice
import com.snabbit.prototype.mobile.domain.auth.AuthSession
import com.snabbit.prototype.mobile.domain.auth.ChangePasswordUseCase
import com.snabbit.prototype.mobile.domain.auth.EmailAuthRepository
import com.snabbit.prototype.mobile.domain.auth.ForgotPasswordUseCase
import com.snabbit.prototype.mobile.domain.auth.LoginWithPasswordUseCase
import com.snabbit.prototype.mobile.domain.auth.PasswordCredentialSaver
import com.snabbit.prototype.mobile.domain.auth.PasswordRules
import com.snabbit.prototype.mobile.domain.auth.RegisterUseCase
import com.snabbit.prototype.mobile.domain.auth.ResendVerificationUseCase
import com.snabbit.prototype.mobile.domain.auth.ResetPasswordUseCase
import com.snabbit.prototype.mobile.domain.auth.SessionUser
import com.snabbit.prototype.mobile.domain.auth.VerifyEmailUseCase
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EmailAuthViewModelsTest {
    @Test fun `auth tabs validate on submit and password login ignores Google configuration`() = runTest {
        val repository = FakeEmailRepository()
        val saver = RecordingCredentialSaver()
        val viewModel = authViewModel(repository, saver)

        viewModel.selectTab(AuthTab.SIGN_UP)
        viewModel.submit()
        assertTrue(viewModel.uiState.value.nameError)
        assertTrue(viewModel.uiState.value.emailError)
        assertTrue(viewModel.uiState.value.passwordError)
        assertTrue(viewModel.uiState.value.termsError)

        viewModel.selectTab(AuthTab.LOGIN)
        viewModel.updateLoginEmail(testEmail())
        viewModel.updateLoginPassword(testPassword())
        val event = async(start = CoroutineStart.UNDISPATCHED) { viewModel.events.first() }
        viewModel.submit()
        viewModel.submit()
        runCurrent()

        assertEquals(AuthUiEvent.Authenticated, event.await())
        assertEquals(1, repository.loginCalls)
        assertEquals(testEmail(), saver.savedEmail)
        assertEquals("", viewModel.uiState.value.loginPassword)
    }

    @Test fun `registration stores pending credential and opens verification`() = runTest {
        val repository = FakeEmailRepository()
        val pending = InMemoryPendingPasswordCredentialStore()
        val viewModel = authViewModel(repository, pending = pending)
        val event = async(start = CoroutineStart.UNDISPATCHED) { viewModel.events.first() }

        viewModel.selectTab(AuthTab.SIGN_UP)
        viewModel.updateSignUpName("Test Customer")
        viewModel.updateSignUpEmail(testEmail())
        viewModel.updateSignUpPassword(testPassword())
        viewModel.setTermsAccepted(true)
        viewModel.submit()
        runCurrent()

        assertEquals(AuthUiEvent.VerifyEmail(testEmail()), event.await())
        assertEquals(testEmail(), pending.consume(testEmail())?.first)
        assertEquals("", viewModel.uiState.value.signUpPassword)
    }

    @Test fun `unverified login opens verification and server fields highlight inputs`() = runTest {
        val repository = FakeEmailRepository(
            loginFailure = AuthFailure(AuthFailureReason.EMAIL_NOT_VERIFIED),
            registerFailure = AuthFailure(
                AuthFailureReason.VALIDATION_ERROR,
                fieldErrors = mapOf("email" to "invalid", "password" to "invalid"),
            ),
        )
        val viewModel = authViewModel(repository)
        val event = async(start = CoroutineStart.UNDISPATCHED) { viewModel.events.first() }
        viewModel.updateLoginEmail(testEmail())
        viewModel.updateLoginPassword(testPassword())
        viewModel.submit()
        runCurrent()
        assertEquals(AuthUiEvent.VerifyEmail(testEmail()), event.await())

        viewModel.selectTab(AuthTab.SIGN_UP)
        viewModel.updateSignUpName("Test Customer")
        viewModel.updateSignUpEmail(testEmail())
        viewModel.updateSignUpPassword(testPassword())
        viewModel.setTermsAccepted(true)
        viewModel.submit()
        runCurrent()
        assertTrue(viewModel.uiState.value.emailError)
        assertTrue(viewModel.uiState.value.passwordError)
    }

    @Test fun `verification auto submits sixth digit and resend countdown gates requests`() = runTest {
        val repository = FakeEmailRepository()
        val pending = InMemoryPendingPasswordCredentialStore().apply { save(testEmail(), testPassword()) }
        val saver = RecordingCredentialSaver()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = VerifyEmailViewModel(
            testEmail(), VerifyEmailUseCase(repository), ResendVerificationUseCase(repository), pending, saver, dispatcher,
        )
        val event = async(start = CoroutineStart.UNDISPATCHED) { viewModel.events.first() }

        viewModel.updateCode(testCode())
        runCurrent()

        assertEquals(VerifyEmailUiEvent.Authenticated, event.await())
        assertEquals(1, repository.verifyCalls)
        assertEquals(testEmail(), saver.savedEmail)
        assertEquals("", viewModel.uiState.value.code)

        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(0L, viewModel.uiState.value.resendSeconds)
        viewModel.resend()
        viewModel.resend()
        runCurrent()
        assertEquals(1, repository.resendCalls)
        assertEquals(60L, viewModel.uiState.value.resendSeconds)
    }

    @Test fun `forgot reset publishes login notice and clears secrets`() = runTest {
        val repository = FakeEmailRepository()
        val notices = InMemoryAuthNoticeStore()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = ForgotPasswordViewModel(
            testEmail(), ForgotPasswordUseCase(repository), ResetPasswordUseCase(repository), PasswordRules(), notices, dispatcher,
        )
        viewModel.submit()
        runCurrent()
        assertEquals(ForgotPasswordStep.RESET, viewModel.uiState.value.step)

        val event = async(start = CoroutineStart.UNDISPATCHED) { viewModel.events.first() }
        viewModel.updateCode(testCode())
        viewModel.updatePassword(testReplacementPassword())
        viewModel.submit()
        runCurrent()

        assertEquals(ForgotPasswordUiEvent.PasswordUpdated, event.await())
        assertEquals(AuthNotice.PASSWORD_UPDATED, notices.consume())
        assertEquals("", viewModel.uiState.value.code)
        assertEquals("", viewModel.uiState.value.newPassword)
    }

    @Test fun `change password emits success and clears both password fields`() = runTest {
        val repository = FakeEmailRepository()
        val viewModel = ChangePasswordViewModel(
            ChangePasswordUseCase(repository), PasswordRules(), StandardTestDispatcher(testScheduler),
        )
        val event = async(start = CoroutineStart.UNDISPATCHED) { viewModel.events.first() }
        viewModel.updateCurrent(testPassword())
        viewModel.updateNew(testReplacementPassword())
        viewModel.submit()
        runCurrent()

        assertEquals(ChangePasswordUiEvent.PasswordChanged, event.await())
        assertEquals("", viewModel.uiState.value.currentPassword)
        assertEquals("", viewModel.uiState.value.newPassword)
        assertFalse(viewModel.uiState.value.isSubmitting)
    }

    private fun kotlinx.coroutines.test.TestScope.authViewModel(
        repository: FakeEmailRepository,
        saver: RecordingCredentialSaver = RecordingCredentialSaver(),
        pending: InMemoryPendingPasswordCredentialStore = InMemoryPendingPasswordCredentialStore(),
    ) = AuthViewModel(
        LoginWithPasswordUseCase(repository), RegisterUseCase(repository), PasswordRules(), pending,
        saver, InMemoryAuthNoticeStore(), StandardTestDispatcher(testScheduler),
    )
}

private class RecordingCredentialSaver : PasswordCredentialSaver {
    var savedEmail: String? = null
    override suspend fun save(email: String, password: String) { savedEmail = email }
}

private class FakeEmailRepository(
    private val loginFailure: Throwable? = null,
    private val registerFailure: Throwable? = null,
) : EmailAuthRepository {
    var loginCalls = 0
    var verifyCalls = 0
    var resendCalls = 0
    override suspend fun register(name: String, email: String, password: String, device: AuthDevice) {
        registerFailure?.let { throw it }
    }
    override suspend fun verifyEmail(email: String, code: String, device: AuthDevice): AuthSession {
        verifyCalls++
        return testSession()
    }
    override suspend fun resendVerification(email: String) { resendCalls++ }
    override suspend fun login(email: String, password: String, device: AuthDevice): AuthSession {
        loginCalls++
        loginFailure?.let { throw it }
        return testSession()
    }
    override suspend fun forgotPassword(email: String) = Unit
    override suspend fun resetPassword(email: String, code: String, newPassword: String) = Unit
    override suspend fun changePassword(currentPassword: String, newPassword: String) = testSession()
    override suspend fun currentCustomer() = testSession().user
}

private fun testSession() = AuthSession(
    "test-access", "Bearer", 1L, "test-refresh",
    SessionUser("test-id", "Test Customer", testEmail(), null, false, "CUSTOMER", hasPassword = true),
)
private fun testEmail() = "person@example.invalid"
private fun testPassword() = listOf("Synthetic", "Credential", "2042").joinToString("-")
private fun testReplacementPassword() = listOf("Synthetic", "Replacement", "2043").joinToString("-")
private fun testCode() = (1..6).joinToString("")
