package com.snabbit.prototype.mobile.data.network

import com.snabbit.prototype.mobile.data.auth.remote.AuthApi
import com.snabbit.prototype.mobile.data.auth.remote.RefreshSessionRequestDto
import com.snabbit.prototype.mobile.data.auth.remote.SessionResponseDto
import com.snabbit.prototype.mobile.domain.auth.AuthSession
import com.snabbit.prototype.mobile.domain.auth.SessionStore
import com.snabbit.prototype.mobile.domain.auth.SessionUser
import com.snabbit.prototype.mobile.domain.session.SessionManager
import com.snabbit.prototype.mobile.navigation.AppRole
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class SessionTokenRefresher(
    private val store: SessionStore,
    private val sessionManager: SessionManager,
    private val role: AppRole,
    private val problemJson: Json,
    private val refreshCall: suspend (AppRole, String) -> SessionResponseDto,
) : TokenRefresher {
    constructor(
        store: SessionStore,
        sessionManager: SessionManager,
        role: AppRole,
        json: Json,
        baseUrl: String,
    ) : this(store, sessionManager, role, json, createRefreshCall(json, baseUrl))

    private val mutex = Mutex()

    override fun refreshAccessToken(): String? {
        val observed = store.current() ?: return null
        return runBlocking(Dispatchers.IO) {
            mutex.withLock {
                val current = store.current() ?: return@withLock null
                if (current.accessToken != observed.accessToken) return@withLock current.accessToken
                refreshWithRetry(current)
            }
        }
    }

    private suspend fun refreshWithRetry(original: AuthSession): String? {
        repeat(REFRESH_IN_PROGRESS_ATTEMPTS) { attempt ->
            try {
                val response = refreshCall(role, original.refreshToken)
                val replacement = response.toDomain()
                store.save(replacement)
                return replacement.accessToken
            } catch (_: IOException) {
                return null
            } catch (failure: HttpException) {
                val code = failure.response()?.errorBody()?.string()?.let { body ->
                    runCatching { problemJson.decodeFromString<ApiProblem>(body).code }.getOrNull()
                }
                if (code == "REFRESH_IN_PROGRESS" && attempt < REFRESH_IN_PROGRESS_ATTEMPTS - 1) {
                    delay(REFRESH_RETRY_DELAY_MILLIS)
                    store.current()?.takeIf { it.refreshToken != original.refreshToken }?.let { return it.accessToken }
                    return@repeat
                }
                if (code == "SESSION_REVOKED" || failure.code() in 400..499 && code != "REFRESH_IN_PROGRESS") {
                    sessionManager.signOut()
                }
                return null
            } catch (_: Exception) {
                return null
            }
        }
        return null
    }

    private fun SessionResponseDto.toDomain() = AuthSession(
        accessToken,
        tokenType,
        System.currentTimeMillis() / 1_000L + expiresIn,
        refreshToken,
        SessionUser(
            user.id,
            user.name,
            user.email,
            user.pictureUrl,
            user.isNewUser,
            user.role,
            store.current()?.user?.phoneNumber,
        ),
    )

    private companion object {
        const val REFRESH_IN_PROGRESS_ATTEMPTS = 3
        const val REFRESH_RETRY_DELAY_MILLIS = 300L

        fun createRefreshCall(json: Json, baseUrl: String): suspend (AppRole, String) -> SessionResponseDto {
            val api = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(OkHttpClient.Builder().build())
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(AuthApi::class.java)
            return { selectedRole, refreshToken ->
                when (selectedRole) {
                    AppRole.CUSTOMER -> api.refreshCustomer(RefreshSessionRequestDto(refreshToken))
                    AppRole.WORKER -> api.refreshWorker(RefreshSessionRequestDto(refreshToken))
                }
            }
        }
    }
}
