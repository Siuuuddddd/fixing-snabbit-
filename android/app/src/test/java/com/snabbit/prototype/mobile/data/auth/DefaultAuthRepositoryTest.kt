package com.snabbit.prototype.mobile.data.auth

import com.snabbit.prototype.mobile.data.auth.remote.AuthRemoteDataSource
import com.snabbit.prototype.mobile.data.auth.remote.GoogleSignInRequestDto
import com.snabbit.prototype.mobile.data.auth.remote.RemoteGoogleSignInResult
import com.snabbit.prototype.mobile.data.auth.remote.SessionResponseDto
import com.snabbit.prototype.mobile.data.auth.remote.SessionUserDto
import com.snabbit.prototype.mobile.domain.auth.AuthDevice
import com.snabbit.prototype.mobile.domain.auth.AuthSession
import com.snabbit.prototype.mobile.domain.auth.GoogleIdToken
import com.snabbit.prototype.mobile.domain.auth.GoogleSignInResult
import com.snabbit.prototype.mobile.domain.auth.SessionStore
import com.snabbit.prototype.mobile.navigation.AppRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultAuthRepositoryTest {
    @Test
    fun `worker Google sign in uses worker endpoint and stores rotating session`() = runTest {
        val remote = RecordingRemoteDataSource()
        val sessions = InMemorySessionStore()
        val repository = DefaultAuthRepository(remote, AppRole.WORKER, Json, sessions)

        val result = repository.signInWithGoogle(GoogleIdToken("id-token", "nonce"), AuthDevice())

        assertEquals(AppRole.WORKER, remote.role)
        assertEquals("nonce", remote.request?.nonce)
        assertEquals("secure-access-token", sessions.current()?.accessToken)
        assertEquals("rotating-refresh-token", sessions.current()?.refreshToken)
        assertTrue(result is GoogleSignInResult.Authenticated)
    }
}

private class RecordingRemoteDataSource : AuthRemoteDataSource {
    var role: AppRole? = null
    var request: GoogleSignInRequestDto? = null

    override suspend fun signInWithGoogle(role: AppRole, request: GoogleSignInRequestDto): RemoteGoogleSignInResult {
        this.role = role
        this.request = request
        return RemoteGoogleSignInResult.Session(
            SessionResponseDto(
                accessToken = "secure-access-token",
                expiresIn = 900,
                refreshToken = "rotating-refresh-token",
                user = SessionUserDto("worker-id", "Savita", "worker@example.invalid", role = "WORKER"),
            ),
        )
    }
}

private class InMemorySessionStore : SessionStore {
    private val value = MutableStateFlow<AuthSession?>(null)
    override val session = value
    override fun current(): AuthSession? = value.value
    override fun save(session: AuthSession) { value.value = session }
    override fun clear() { value.value = null }
}
