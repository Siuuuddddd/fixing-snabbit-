package com.snabbit.prototype.mobile.domain.auth

interface EmailAuthRepository {
    suspend fun register(name: String, email: String, password: String, device: AuthDevice = AuthDevice())
    suspend fun verifyEmail(email: String, code: String, device: AuthDevice = AuthDevice()): AuthSession =
        error("Email verification is not implemented")
    suspend fun verifyEmail(email: String, code: String, rememberMe: Boolean, device: AuthDevice = AuthDevice()): AuthSession =
        verifyEmail(email, code, device)
    suspend fun resendVerification(email: String)
    suspend fun login(email: String, password: String, device: AuthDevice = AuthDevice()): AuthSession =
        error("Email login is not implemented")
    suspend fun login(email: String, password: String, rememberMe: Boolean, device: AuthDevice = AuthDevice()): AuthSession =
        login(email, password, device)
    suspend fun forgotPassword(email: String)
    suspend fun resetPassword(email: String, code: String, newPassword: String)
    suspend fun changePassword(currentPassword: String, newPassword: String): AuthSession
    suspend fun currentCustomer(): SessionUser
}

