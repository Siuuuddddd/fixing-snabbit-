package com.snabbit.prototype.mobile.data.network

import com.snabbit.prototype.mobile.domain.auth.SessionStore
import com.snabbit.prototype.mobile.data.auth.TokenStore
import com.snabbit.prototype.mobile.domain.auth.AuthSession
import com.snabbit.prototype.mobile.domain.auth.SessionUser
import com.snabbit.prototype.mobile.domain.profile.PhoneRequiredEvents
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(
    private val sessionStore: SessionStore,
    private val phoneRequiredEvents: PhoneRequiredEvents? = null,
    private val json: Json? = null,
) : Interceptor {
    constructor(tokenStore: TokenStore) : this(LegacyTokenSessionStore(tokenStore))
    override fun intercept(chain: Interceptor.Chain): Response {
        if (chain.request().url.encodedPath.isPublicAuthenticationPath()) {
            return chain.proceed(chain.request())
        }
        val token = sessionStore.current()?.accessToken
        val request = if (token.isNullOrBlank()) {
            chain.request()
        } else {
            chain.request().newBuilder().header(AUTHORIZATION, "Bearer $token").build()
        }
        return chain.proceed(request).also(::detectPhoneRequired)
    }

    private fun detectPhoneRequired(response: Response) {
        if (response.code != 409) return
        val events = phoneRequiredEvents ?: return
        val parser = json ?: return
        val code = runCatching {
            parser.decodeFromString<ApiProblem>(response.peekBody(MAX_PROBLEM_BYTES).string()).code
        }.getOrNull()
        if (code == "PHONE_REQUIRED") events.notifyRequired()
    }

    private companion object {
        const val AUTHORIZATION = "Authorization"
        const val MAX_PROBLEM_BYTES = 16_384L
    }
}

private class LegacyTokenSessionStore(private val tokenStore: TokenStore) : SessionStore {
    override val session: Flow<AuthSession?> = flowOf(current())
    override fun current(): AuthSession? = tokenStore.accessToken()?.let { token ->
        AuthSession(
            token,
            "Bearer",
            Long.MAX_VALUE,
            "legacy",
            SessionUser("legacy", "", "", null, false, "CUSTOMER"),
        )
    }
    override fun save(session: AuthSession) = tokenStore.saveAccessToken(session.accessToken)
    override fun clear() = tokenStore.clear()
}

private fun String.isPublicAuthenticationPath(): Boolean =
    this == "/api/v1/auth/google" || this == "/api/v1/auth/refresh" ||
        this == "/api/v1/worker-auth/google" || this == "/api/v1/worker-auth/refresh" ||
        this == "/api/v1/worker-auth/select-profile"
