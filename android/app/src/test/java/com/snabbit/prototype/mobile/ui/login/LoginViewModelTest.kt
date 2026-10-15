package com.snabbit.prototype.mobile.ui.login

import com.snabbit.prototype.mobile.data.auth.InMemoryPendingWorkerSelectionStore
import com.snabbit.prototype.mobile.domain.auth.AuthDevice
import com.snabbit.prototype.mobile.domain.auth.AuthFailure
import com.snabbit.prototype.mobile.domain.auth.AuthFailureReason
import com.snabbit.prototype.mobile.domain.auth.AuthRepository
import com.snabbit.prototype.mobile.domain.auth.AuthResult
import com.snabbit.prototype.mobile.domain.auth.AuthSession
import com.snabbit.prototype.mobile.domain.auth.GoogleIdToken
import com.snabbit.prototype.mobile.domain.auth.GoogleIdTokenProvider
import com.snabbit.prototype.mobile.domain.auth.GoogleSignInResult
import com.snabbit.prototype.mobile.domain.auth.OtpChallenge
import com.snabbit.prototype.mobile.domain.auth.SignInWithGoogleUseCase
import com.snabbit.prototype.mobile.domain.auth.WorkerPickerOption
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {
    @Test
    fun `successful sign in emits authenticated`() = runTest {
        val repository = FakeGoogleAuthRepository(GoogleSignInResult.Authenticated(session()))
        val viewModel = viewModel(repository)
        val event = async { viewModel.events.first() }

        viewModel.continueWithGoogle()
        runCurrent()

        assertEquals(LoginUiEvent.Authenticated, event.await())
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(1, repository.signInCalls)
    }

    @Test
    fun `worker picker result is kept only in memory`() = runTest {
        val pending = InMemoryPendingWorkerSelectionStore()
        val result = GoogleSignInResult.WorkerSelectionRequired(
            "selection",
            1234,
            listOf(WorkerPickerOption("worker", "Savita", "EXPERT")),
        )
        val viewModel = viewModel(FakeGoogleAuthRepository(result), pending)
        val event = async { viewModel.events.first() }

        viewModel.continueWithGoogle()
        runCurrent()

        assertEquals(LoginUiEvent.OpenWorkerPicker, event.await())
        assertEquals("selection", pending.current()?.selectionToken)
    }

    @Test
    fun `cancelled sign in returns silently to idle`() = runTest {
        val repository = FakeGoogleAuthRepository(failure = AuthFailure(AuthFailureReason.CANCELLED))
        val viewModel = viewModel(repository)

        viewModel.continueWithGoogle()
        runCurrent()

        assertNull(viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `double tap starts one request`() = runTest {
        val repository = FakeGoogleAuthRepository(GoogleSignInResult.Authenticated(session()))
        val viewModel = viewModel(repository)

        viewModel.continueWithGoogle()
        viewModel.continueWithGoogle()
        runCurrent()

        assertEquals(1, repository.signInCalls)
    }

    @Test
    fun `blank client configuration disables sign in`() = runTest {
        val repository = FakeGoogleAuthRepository(GoogleSignInResult.Authenticated(session()))
        val viewModel = viewModel(repository, configured = false)

        viewModel.continueWithGoogle()
        runCurrent()

        assertEquals(LoginError.NOT_CONFIGURED, viewModel.uiState.value.error)
        assertEquals(0, repository.signInCalls)
    }

    private fun TestScope.viewModel(
        repository: FakeGoogleAuthRepository,
        pending: InMemoryPendingWorkerSelectionStore = InMemoryPendingWorkerSelectionStore(),
        configured: Boolean = true,
    ) = LoginViewModel(
        SignInWithGoogleUseCase(FakeGoogleIdTokenProvider(), repository),
        pending,
        configured,
        StandardTestDispatcher(testScheduler),
    )
}

private class FakeGoogleIdTokenProvider : GoogleIdTokenProvider {
    override suspend fun requestIdToken() = GoogleIdToken("id-token", "nonce")
    override suspend fun clearCredentialState() = Unit
}

private class FakeGoogleAuthRepository(
    private val result: GoogleSignInResult? = null,
    private val failure: Throwable? = null,
) : AuthRepository {
    var signInCalls = 0
    override suspend fun signInWithGoogle(credential: GoogleIdToken, device: AuthDevice): GoogleSignInResult {
        signInCalls++
        failure?.let { throw it }
        return requireNotNull(result)
    }
    override suspend fun requestOtp(phoneNumber: String): OtpChallenge = error("unused")
    override suspend fun verifyOtp(phoneNumber: String, otp: String): AuthResult = error("unused")
    override suspend fun register(phoneNumber: String, displayName: String, registrationToken: String) = Unit
}

private fun session() = AuthSession(
    "access",
    "Bearer",
    1234,
    "refresh",
    com.snabbit.prototype.mobile.domain.auth.SessionUser("id", "Name", "user@example.invalid", null, false, "CUSTOMER"),
)
