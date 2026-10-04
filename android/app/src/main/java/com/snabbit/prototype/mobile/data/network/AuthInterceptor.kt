package com.snabbit.prototype.mobile.data.network

import com.snabbit.prototype.mobile.data.auth.TokenStore
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(private val tokenStore: TokenStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        if (chain.request().url.encodedPath.isPublicAuthenticationPath()) {
            return chain.proceed(chain.request())
        }
        val token = tokenStore.accessToken()
        val request = if (token.isNullOrBlank()) {
            chain.request()
        } else {
            chain.request().newBuilder().header(AUTHORIZATION, "Bearer $token").build()
        }
        return chain.proceed(request)
    }

    private companion object {
        const val AUTHORIZATION = "Authorization"
    }
}

private fun String.isPublicAuthenticationPath(): Boolean =
    startsWith("/api/v1/auth/") || startsWith("/api/v1/worker-auth/")
