package com.snabbit.prototype.mobile.data.auth.remote

import retrofit2.http.Body
import retrofit2.http.POST
import kotlinx.serialization.json.JsonObject

interface AuthApi {
    @POST("api/v1/auth/google")
    suspend fun googleCustomer(@Body request: GoogleSignInRequestDto): SessionResponseDto

    @POST("api/v1/worker-auth/google")
    suspend fun googleWorker(@Body request: GoogleSignInRequestDto): JsonObject

    @POST("api/v1/worker-auth/select-profile")
    suspend fun selectWorkerProfile(@Body request: WorkerSelectProfileRequestDto): SessionResponseDto

    @POST("api/v1/worker-auth/switch-profile")
    suspend fun switchWorkerProfile(@Body request: WorkerSwitchProfileRequestDto): SessionResponseDto

    @POST("api/v1/auth/refresh")
    suspend fun refreshCustomer(@Body request: RefreshSessionRequestDto): SessionResponseDto

    @POST("api/v1/worker-auth/refresh")
    suspend fun refreshWorker(@Body request: RefreshSessionRequestDto): SessionResponseDto

    @POST("api/v1/auth/logout")
    suspend fun logoutCustomer()

    @POST("api/v1/auth/logout-all")
    suspend fun logoutAllCustomers()

    @POST("api/v1/worker-auth/logout")
    suspend fun logoutWorker()

    @POST("api/v1/worker-auth/logout-all")
    suspend fun logoutAllWorkers()

    @POST("api/v1/auth/otp/request")
    suspend fun requestCustomerOtp(@Body request: OtpRequestDto): OtpChallengeDto

    @POST("api/v1/auth/otp/verify")
    suspend fun verifyCustomerOtp(@Body request: OtpVerificationDto): AccessTokenDto

    @POST("api/v1/auth/register")
    suspend fun registerCustomer(@Body request: CustomerRegistrationDto): AccessTokenDto

    @POST("api/v1/worker-auth/otp/request")
    suspend fun requestWorkerOtp(@Body request: OtpRequestDto): OtpChallengeDto

    @POST("api/v1/worker-auth/otp/verify")
    suspend fun verifyWorkerOtp(@Body request: OtpVerificationDto): AccessTokenDto
}
