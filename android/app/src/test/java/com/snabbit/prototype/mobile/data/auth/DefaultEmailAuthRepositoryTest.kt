package com.snabbit.prototype.mobile.data.auth

import com.snabbit.prototype.mobile.data.auth.remote.SessionResponseDto
import com.snabbit.prototype.mobile.data.auth.remote.SessionUserDto
import com.snabbit.prototype.mobile.data.auth.remote.email.ChangePasswordRequestDto
import com.snabbit.prototype.mobile.data.auth.remote.email.EmailAuthRemoteDataSource
import com.snabbit.prototype.mobile.data.auth.remote.email.EmailLoginRequestDto
import com.snabbit.prototype.mobile.data.auth.remote.email.EmailOnlyRequestDto
import com.snabbit.prototype.mobile.data.auth.remote.email.EmailRegisterRequestDto
import com.snabbit.prototype.mobile.data.auth.remote.email.ResetPasswordRequestDto
import com.snabbit.prototype.mobile.data.auth.remote.email.VerifyEmailRequestDto
import com.snabbit.prototype.mobile.domain.auth.AuthFailure
import com.snabbit.prototype.mobile.domain.auth.AuthFailureReason
import com.snabbit.prototype.mobile.domain.auth.AuthSession
import com.snabbit.prototype.mobile.domain.auth.SessionStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class DefaultEmailAuthRepositoryTest {
    @Test fun `verify login and change password store complete rotating sessions`() = runTest {
        val sessions = MemoryEmailSessionStore()
        val repository = DefaultEmailAuthRepository(FakeEmailRemote(), sessions, Json, { 100L })

        repository.verifyEmail(" Person@Example.Invalid ", code())
        assertEquals("person@example.invalid", sessions.current()?.user?.email)
        assertEquals(1000L, sessions.current()?.expiresAtEpochSeconds)
        assertTrue(sessions.current()?.user?.hasPassword == true)
        assertEquals(setOf("PASSWORD"), sessions.current()?.user?.authProviders)
        repository.login("person@example.invalid", credential())
        repository.changePassword(credential(), replacementCredential())
        assertEquals(3, sessions.saves)
    }

    @Test fun `reset clears every local session after backend success`() = runTest {
        val sessions = MemoryEmailSessionStore().apply { save(session()) }
        DefaultEmailAuthRepository(FakeEmailRemote(), sessions, Json).resetPassword(
            "person@example.invalid", code(), replacementCredential(),
        )
        assertNull(sessions.current())
    }

    @Test fun `all backend error codes preserve retry fields and weak password reason`() = runTest {
        val expected = mapOf(
            "INVALID_CREDENTIALS" to AuthFailureReason.INVALID_CREDENTIALS,
            "EMAIL_NOT_VERIFIED" to AuthFailureReason.EMAIL_NOT_VERIFIED,
            "WEAK_PASSWORD" to AuthFailureReason.WEAK_PASSWORD,
            "INVALID_OR_EXPIRED_CODE" to AuthFailureReason.INVALID_OR_EXPIRED_CODE,
            "TOO_MANY_ATTEMPTS" to AuthFailureReason.TOO_MANY_ATTEMPTS,
            "RATE_LIMITED" to AuthFailureReason.RATE_LIMITED,
            "TERMS_NOT_ACCEPTED" to AuthFailureReason.TERMS_NOT_ACCEPTED,
            "ACCOUNT_DISABLED" to AuthFailureReason.ACCOUNT_DISABLED,
            "PASSWORD_NOT_SET" to AuthFailureReason.PASSWORD_NOT_SET,
            "VALIDATION_ERROR" to AuthFailureReason.VALIDATION_ERROR,
            "SESSION_REVOKED" to AuthFailureReason.SESSION_REVOKED,
        )
        expected.forEach { (code, reason) ->
            val repository = DefaultEmailAuthRepository(FakeEmailRemote(problem = code), MemoryEmailSessionStore(), Json)
            val failure = runCatching { repository.login("person@example.invalid", credential()) }.exceptionOrNull() as AuthFailure
            assertEquals(reason, failure.reason)
            assertEquals(17L, failure.retryAfterSeconds)
            assertEquals("email", failure.fieldErrors.keys.single())
            assertEquals("LOCAL_RULE", failure.passwordReason)
        }
    }
}

private class FakeEmailRemote(private val problem: String? = null) : EmailAuthRemoteDataSource {
    private fun failIfNeeded() {
        val code = problem ?: return
        val body = """{"code":"$code","detail":"safe","retryAfterSeconds":17,"fields":{"email":"invalid"},"reason":"LOCAL_RULE"}"""
        throw HttpException(Response.error<Any>(400, body.toResponseBody("application/problem+json".toMediaType())))
    }
    override suspend fun register(request: EmailRegisterRequestDto) = failIfNeeded()
    override suspend fun verifyEmail(request: VerifyEmailRequestDto): SessionResponseDto { failIfNeeded(); return response(request.email) }
    override suspend fun resendVerification(request: EmailOnlyRequestDto) = failIfNeeded()
    override suspend fun login(request: EmailLoginRequestDto): SessionResponseDto { failIfNeeded(); return response(request.email) }
    override suspend fun forgotPassword(request: EmailOnlyRequestDto) = failIfNeeded()
    override suspend fun resetPassword(request: ResetPasswordRequestDto) = failIfNeeded()
    override suspend fun changePassword(request: ChangePasswordRequestDto): SessionResponseDto { failIfNeeded(); return response("person@example.invalid") }
    override suspend fun currentCustomer() = response("person@example.invalid").user
    private fun response(email: String) = SessionResponseDto(
        "access", expiresIn = 900, refreshToken = "refresh",
        user = SessionUserDto("id", "Test", email, role = "CUSTOMER", emailVerified = true, hasPassword = true, authProviders = listOf("PASSWORD")),
    )
}

private class MemoryEmailSessionStore : SessionStore {
    private val value = MutableStateFlow<AuthSession?>(null)
    var saves = 0
    override val session = value
    override fun current() = value.value
    override fun save(session: AuthSession) { saves++; value.value = session }
    override fun clear() { value.value = null }
}

private fun session() = AuthSession("access", "Bearer", 1L, "refresh", com.snabbit.prototype.mobile.domain.auth.SessionUser("id", "Test", "person@example.invalid", null, false, "CUSTOMER"))
private fun credential() = "Synthetic!Credential-2042"
private fun replacementCredential() = "Synthetic!Replacement-2043"
private fun code() = (1..6).joinToString("") { it.toString() }
