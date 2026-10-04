package com.snabbit.prototype.mobile.data.auth.remote

import com.snabbit.prototype.mobile.navigation.AppRole

interface AuthRemoteDataSource {
    suspend fun requestOtp(role: AppRole, phoneNumber: String): OtpChallengeDto
    suspend fun verifyOtp(role: AppRole, phoneNumber: String, otp: String): AccessTokenDto
}

class RetrofitAuthRemoteDataSource(
    private val api: AuthApi,
) : AuthRemoteDataSource {
    override suspend fun requestOtp(role: AppRole, phoneNumber: String): OtpChallengeDto {
        val request = OtpRequestDto(phoneNumber)
        return when (role) {
            AppRole.CUSTOMER -> api.requestCustomerOtp(request)
            AppRole.WORKER -> api.requestWorkerOtp(request)
        }
    }

    override suspend fun verifyOtp(role: AppRole, phoneNumber: String, otp: String): AccessTokenDto {
        val request = OtpVerificationDto(phoneNumber, otp)
        return when (role) {
            AppRole.CUSTOMER -> api.verifyCustomerOtp(request)
            AppRole.WORKER -> api.verifyWorkerOtp(request)
        }
    }
}
