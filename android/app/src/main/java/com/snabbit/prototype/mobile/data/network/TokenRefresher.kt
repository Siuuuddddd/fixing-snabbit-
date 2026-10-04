package com.snabbit.prototype.mobile.data.network

interface TokenRefresher {
    fun refreshAccessToken(): String?
}
