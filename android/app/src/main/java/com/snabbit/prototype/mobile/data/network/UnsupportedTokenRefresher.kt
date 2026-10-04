package com.snabbit.prototype.mobile.data.network

import com.snabbit.prototype.mobile.data.auth.TokenStore

class UnsupportedTokenRefresher(private val tokenStore: TokenStore) : TokenRefresher {
    override fun refreshAccessToken(): String? {
        tokenStore.clear()
        return null
    }
}
