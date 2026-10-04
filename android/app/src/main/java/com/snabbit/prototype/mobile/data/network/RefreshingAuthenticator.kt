package com.snabbit.prototype.mobile.data.network

import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

class RefreshingAuthenticator(private val tokenRefresher: TokenRefresher) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.responseCount() >= MAX_ATTEMPTS) return null
        val refreshedToken = tokenRefresher.refreshAccessToken() ?: return null
        return response.request.newBuilder().header(AUTHORIZATION, "Bearer $refreshedToken").build()
    }

    private fun Response.responseCount(): Int {
        var count = 1
        var current = priorResponse
        while (current != null) {
            count++
            current = current.priorResponse
        }
        return count
    }

    private companion object {
        const val AUTHORIZATION = "Authorization"
        const val MAX_ATTEMPTS = 2
    }
}
