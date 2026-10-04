package com.snabbit.prototype.mobile.data.auth

interface TokenStore {
    fun accessToken(): String?
    fun saveAccessToken(token: String)
    fun clear()
}
