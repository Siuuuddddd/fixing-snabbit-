package com.snabbit.prototype.mobile.domain.auth

interface GoogleIdTokenProvider {
    suspend fun requestIdToken(): GoogleIdToken
    suspend fun clearCredentialState()
}
