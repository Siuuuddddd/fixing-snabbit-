package com.snabbit.prototype.mobile.domain.auth

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthUseCasesTest {
    @Test fun `sign in forwards fresh Google credential and device`() = runTest {
        val repository = RecordingAuthRepository()
        val provider = RecordingGoogleProvider()
        val device = AuthDevice("device", "Pixel")

        val result = SignInWithGoogleUseCase(provider, repository)(device)

        assertTrue(result is GoogleSignInResult.Authenticated)
        assertEquals(GoogleIdToken("id-token", "nonce"), repository.googleCredential)
        assertEquals(device, repository.device)
    }

    @Test fun `sign in propagates every documented backend auth failure`() = runTest {
        val reasons = listOf(
            AuthFailureReason.INVALID_GOOGLE_TOKEN,
            AuthFailureReason.EMAIL_NOT_VERIFIED,
            AuthFailureReason.WORKER_NOT_REGISTERED,
            AuthFailureReason.ACCOUNT_DISABLED,
            AuthFailureReason.ACCOUNT_LINK_CONFLICT,
            AuthFailureReason.RATE_LIMITED,
        )
        reasons.forEach { reason ->
            val repository = RecordingAuthRepository(signInFailure = AuthFailure(reason))
            val failure = runCatching { SignInWithGoogleUseCase(RecordingGoogleProvider(), repository)() }.exceptionOrNull()
            assertEquals(reason, (failure as AuthFailure).reason)
        }
    }

    @Test fun `worker selection reacquires credential and forwards selection`() = runTest {
        val repository = RecordingAuthRepository()
        val provider = RecordingGoogleProvider()

        SelectWorkerProfileUseCase(provider, repository)("selection", "worker")

        assertEquals("selection", repository.selectionToken)
        assertEquals("worker", repository.workerId)
        assertEquals(1, provider.requests)
    }

    @Test fun `switch refresh and both logout modes call repository`() = runTest {
        val repository = RecordingAuthRepository()
        val provider = RecordingGoogleProvider()

        SwitchWorkerProfileUseCase(repository)("worker-2")
        RefreshSessionUseCase(repository)()
        SignOutUseCase(repository, provider)()
        SignOutAllUseCase(repository, provider)()

        assertEquals("worker-2", repository.switchedWorkerId)
        assertEquals(1, repository.refreshCalls)
        assertEquals(listOf(false, true), repository.logoutModes)
        assertEquals(2, provider.clears)
    }

    @Test fun `credential state clears even when backend logout fails`() = runTest {
        val repository = RecordingAuthRepository(logoutFailure = IllegalStateException("offline"))
        val provider = RecordingGoogleProvider()

        assertFalse(runCatching { SignOutUseCase(repository, provider)() }.isSuccess)
        assertEquals(1, provider.clears)
    }
}

private class RecordingGoogleProvider : GoogleIdTokenProvider {
    var requests = 0
    var clears = 0
    override suspend fun requestIdToken(): GoogleIdToken {
        requests++
        return GoogleIdToken("id-token", "nonce")
    }
    override suspend fun clearCredentialState() { clears++ }
}

private class RecordingAuthRepository(
    private val signInFailure: Throwable? = null,
    private val logoutFailure: Throwable? = null,
) : AuthRepository {
    var googleCredential: GoogleIdToken? = null
    var device: AuthDevice? = null
    var selectionToken: String? = null
    var workerId: String? = null
    var switchedWorkerId: String? = null
    var refreshCalls = 0
    val logoutModes = mutableListOf<Boolean>()

    override suspend fun signInWithGoogle(credential: GoogleIdToken, device: AuthDevice): GoogleSignInResult {
        signInFailure?.let { throw it }
        googleCredential = credential
        this.device = device
        return GoogleSignInResult.Authenticated(session())
    }
    override suspend fun selectWorkerProfile(
        credential: GoogleIdToken,
        selectionToken: String,
        workerId: String,
        device: AuthDevice,
    ): AuthSession {
        googleCredential = credential
        this.selectionToken = selectionToken
        this.workerId = workerId
        return session()
    }
    override suspend fun switchWorkerProfile(workerId: String): AuthSession {
        switchedWorkerId = workerId
        return session()
    }
    override suspend fun refreshSession(): AuthSession {
        refreshCalls++
        return session()
    }
    override suspend fun signOut(allDevices: Boolean) {
        logoutModes += allDevices
        logoutFailure?.let { throw it }
    }
}

private fun session() = AuthSession(
    "access", "Bearer", 1234, "refresh",
    SessionUser("id", "Name", "person@example.invalid", null, false, "CUSTOMER"),
)
