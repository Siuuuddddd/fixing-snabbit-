package com.snabbit.prototype.mobile.ui.login

import com.snabbit.prototype.mobile.domain.auth.AuthDevice
import com.snabbit.prototype.mobile.domain.auth.AuthFailure
import com.snabbit.prototype.mobile.domain.auth.AuthFailureReason
import com.snabbit.prototype.mobile.domain.auth.AuthRepository
import com.snabbit.prototype.mobile.domain.auth.AuthSession
import com.snabbit.prototype.mobile.domain.auth.GoogleIdToken
import com.snabbit.prototype.mobile.domain.auth.GoogleIdTokenProvider
import com.snabbit.prototype.mobile.domain.auth.GoogleSignInResult
import com.snabbit.prototype.mobile.domain.auth.RequestPhoneOtpUseCase
import com.snabbit.prototype.mobile.domain.auth.SessionUser
import com.snabbit.prototype.mobile.domain.auth.SignInWithGoogleUseCase
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CustomerLoginViewModelTest {
    private val nationalNumber = "9" + "0".repeat(8) + "1"

    @Test
    fun `phone button enables only for a valid Indian mobile and preserves remember me`() = runTest {
        val repository = RecordingAuthRepository()
        val viewModel = viewModel(repository)

        viewModel.updatePhone("123")
        assertFalse(viewModel.uiState.value.canContinue)
        viewModel.continueWithPhone()
        assertEquals(CustomerLoginError.INVALID_PHONE, viewModel.uiState.value.error)

        viewModel.updatePhone(nationalNumber)
        viewModel.setRememberMe(false)
        assertTrue(viewModel.uiState.value.canContinue)
        val event = async(start = CoroutineStart.UNDISPATCHED) { viewModel.events.first() }
        viewModel.continueWithPhone()
        runCurrent()

        assertEquals("+91$nationalNumber", repository.requestedPhone)
        assertEquals(CustomerLoginUiEvent.OpenOtp("+91$nationalNumber", false, 30), event.await())
    }

    @Test
    fun `google sign in forwards remember me and authenticates`() = runTest {
        val repository = RecordingAuthRepository()
        val viewModel = viewModel(repository)
        viewModel.setRememberMe(false)
        val event = async(start = CoroutineStart.UNDISPATCHED) { viewModel.events.first() }

        viewModel.continueWithGoogle()
        runCurrent()

        assertEquals(false, repository.googleRememberMe)
        assertEquals(CustomerLoginUiEvent.Authenticated, event.await())
    }

    @Test
    fun `email action carries remember me preference`() = runTest {
        val viewModel = viewModel(RecordingAuthRepository())
        viewModel.setRememberMe(false)
        val event = async(start = CoroutineStart.UNDISPATCHED) { viewModel.events.first() }
        viewModel.openEmail()
        assertEquals(CustomerLoginUiEvent.OpenEmail(false), event.await())
    }

    @Test
    fun `blank Google configuration does not block phone or email`() = runTest {
        val repository = RecordingAuthRepository()
        val viewModel = CustomerLoginViewModel(
            RequestPhoneOtpUseCase(repository),
            SignInWithGoogleUseCase(FakeGoogleProvider(), repository),
            googleConfigured = false,
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        viewModel.continueWithGoogle()

        assertEquals(CustomerLoginError.NOT_CONFIGURED, viewModel.uiState.value.error)
        viewModel.updatePhone(nationalNumber)
        assertTrue(viewModel.uiState.value.canContinue)
    }

    @Test
    fun `Google no-account outcome is shown and cancellation is silent`() = runTest {
        val repository = RecordingAuthRepository().apply {
            googleFailure = AuthFailure(AuthFailureReason.NO_GOOGLE_ACCOUNT)
        }
        val viewModel = viewModel(repository)

        viewModel.continueWithGoogle()
        runCurrent()
        assertEquals(CustomerLoginError.NO_GOOGLE_ACCOUNT, viewModel.uiState.value.error)

        repository.googleFailure = AuthFailure(AuthFailureReason.CANCELLED)
        viewModel.continueWithGoogle()
        runCurrent()
        assertEquals(null, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    private fun kotlinx.coroutines.test.TestScope.viewModel(repository: RecordingAuthRepository) =
        CustomerLoginViewModel(
            RequestPhoneOtpUseCase(repository),
            SignInWithGoogleUseCase(FakeGoogleProvider(), repository),
            googleConfigured = true,
            dispatcher = StandardTestDispatcher(testScheduler),
        )
}

private class RecordingAuthRepository : AuthRepository {
    var requestedPhone: String? = null
    var googleRememberMe: Boolean? = null
    var googleFailure: AuthFailure? = null

    override suspend fun requestPhoneOtp(phoneNumber: String): Long {
        requestedPhone = phoneNumber
        return 30
    }

    override suspend fun signInWithGoogle(
        credential: GoogleIdToken,
        device: AuthDevice,
        rememberMe: Boolean,
    ): GoogleSignInResult {
        googleRememberMe = rememberMe
        googleFailure?.let { throw it }
        return GoogleSignInResult.Authenticated(testSession())
    }
}

private class FakeGoogleProvider : GoogleIdTokenProvider {
    override suspend fun requestIdToken() = GoogleIdToken("test-token", "test-nonce")
    override suspend fun clearCredentialState() = Unit
}

private fun testSession() = AuthSession(
    accessToken = "access",
    tokenType = "Bearer",
    expiresAtEpochSeconds = 1,
    refreshToken = "refresh",
    user = SessionUser("user", "Test User", null, null, false, "CUSTOMER"),
)
