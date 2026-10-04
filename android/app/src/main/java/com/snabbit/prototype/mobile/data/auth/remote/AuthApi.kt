package com.snabbit.prototype.mobile.data.auth.remote

import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApi {
    @POST("api/v1/auth/otp/request")
    suspend fun requestCustomerOtp(@Body request: OtpRequestDto): OtpChallengeDto

    @POST("api/v1/auth/otp/verify")
    suspend fun verifyCustomerOtp(@Body request: OtpVerificationDto): AccessTokenDto

    @POST("api/v1/worker-auth/otp/request")
    suspend fun requestWorkerOtp(@Body request: OtpRequestDto): OtpChallengeDto

    @POST("api/v1/worker-auth/otp/verify")
    suspend fun verifyWorkerOtp(@Body request: OtpVerificationDto): AccessTokenDto
}
