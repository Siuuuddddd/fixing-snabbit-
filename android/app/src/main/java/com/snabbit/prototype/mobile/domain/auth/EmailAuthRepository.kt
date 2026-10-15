package com.snabbit.prototype.mobile.domain.auth

interface EmailAuthRepository {
    suspend fun register(name: String, email: String, password: String, device: AuthDevice = AuthDevice())
    suspend fun verifyEmail(email: String, code: String, device: AuthDevice = AuthDevice()): AuthSession
    suspend fun resendVerification(email: String)
    suspend fun login(email: String, password: String, device: AuthDevice = AuthDevice()): AuthSession
    suspend fun forgotPassword(email: String)
    suspend fun resetPassword(email: String, code: String, newPassword: String)
    suspend fun changePassword(currentPassword: String, newPassword: String): AuthSession
    suspend fun currentCustomer(): SessionUser
}

