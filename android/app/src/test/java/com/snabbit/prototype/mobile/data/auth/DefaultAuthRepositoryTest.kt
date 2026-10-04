package com.snabbit.prototype.mobile.data.auth

import com.snabbit.prototype.mobile.data.auth.remote.AccessTokenDto
import com.snabbit.prototype.mobile.data.auth.remote.AuthRemoteDataSource
import com.snabbit.prototype.mobile.data.auth.remote.OtpChallengeDto
import com.snabbit.prototype.mobile.navigation.AppRole
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultAuthRepositoryTest {
    @Test
    fun `worker verification uses worker role and stores access token`() = runTest {
        val remote = RecordingRemoteDataSource()
        val tokenStore = InMemoryTokenStore()
        val repository = DefaultAuthRepository(remote, tokenStore, AppRole.WORKER, Json)

        repository.verifyOtp("+919876543210", "123456")

        assertEquals(AppRole.WORKER, remote.role)
        assertEquals("secure-access-token", tokenStore.accessToken())
    }
}

private class RecordingRemoteDataSource : AuthRemoteDataSource {
    var role: AppRole? = null

    override suspend fun requestOtp(role: AppRole, phoneNumber: String): OtpChallengeDto {
        this.role = role
        return OtpChallengeDto("OTP_SENT", 300)
    }

    override suspend fun verifyOtp(role: AppRole, phoneNumber: String, otp: String): AccessTokenDto {
        this.role = role
        return AccessTokenDto("secure-access-token", "Bearer", 900)
    }
}

private class InMemoryTokenStore : TokenStore {
    private var token: String? = null
    override fun accessToken(): String? = token
    override fun saveAccessToken(token: String) {
        this.token = token
    }
    override fun clear() {
        token = null
    }
}
