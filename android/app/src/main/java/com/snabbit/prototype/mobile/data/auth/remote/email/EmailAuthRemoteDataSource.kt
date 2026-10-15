package com.snabbit.prototype.mobile.data.auth.remote.email

import com.snabbit.prototype.mobile.data.auth.remote.AuthApi
import com.snabbit.prototype.mobile.data.auth.remote.SessionResponseDto
import com.snabbit.prototype.mobile.data.auth.remote.SessionUserDto

interface EmailAuthRemoteDataSource {
    suspend fun register(request: EmailRegisterRequestDto)
    suspend fun verifyEmail(request: VerifyEmailRequestDto): SessionResponseDto
    suspend fun resendVerification(request: EmailOnlyRequestDto)
    suspend fun login(request: EmailLoginRequestDto): SessionResponseDto
    suspend fun forgotPassword(request: EmailOnlyRequestDto)
    suspend fun resetPassword(request: ResetPasswordRequestDto)
    suspend fun changePassword(request: ChangePasswordRequestDto): SessionResponseDto
    suspend fun currentCustomer(): SessionUserDto
}

class RetrofitEmailAuthRemoteDataSource(private val api: AuthApi) : EmailAuthRemoteDataSource {
    override suspend fun register(request: EmailRegisterRequestDto) { api.register(request) }
    override suspend fun verifyEmail(request: VerifyEmailRequestDto) = api.verifyEmail(request)
    override suspend fun resendVerification(request: EmailOnlyRequestDto) { api.resendVerification(request) }
    override suspend fun login(request: EmailLoginRequestDto) = api.loginWithPassword(request)
    override suspend fun forgotPassword(request: EmailOnlyRequestDto) { api.forgotPassword(request) }
    override suspend fun resetPassword(request: ResetPasswordRequestDto) = api.resetPassword(request)
    override suspend fun changePassword(request: ChangePasswordRequestDto) = api.changePassword(request)
    override suspend fun currentCustomer() = api.currentCustomer()
}

