package com.snabbit.prototype.mobile.data.auth.remote

import com.snabbit.prototype.mobile.data.auth.remote.email.AcceptedAuthRequestDto
import com.snabbit.prototype.mobile.data.auth.remote.email.ChangePasswordRequestDto
import com.snabbit.prototype.mobile.data.auth.remote.email.EmailLoginRequestDto
import com.snabbit.prototype.mobile.data.auth.remote.email.EmailOnlyRequestDto
import com.snabbit.prototype.mobile.data.auth.remote.email.EmailRegisterRequestDto
import com.snabbit.prototype.mobile.data.auth.remote.email.ResetPasswordRequestDto
import com.snabbit.prototype.mobile.data.auth.remote.email.VerifyEmailRequestDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import kotlinx.serialization.json.JsonObject

interface AuthApi {
    @POST("api/v1/auth/register")
    suspend fun register(@Body request: EmailRegisterRequestDto): AcceptedAuthRequestDto

    @POST("api/v1/auth/verify-email")
    suspend fun verifyEmail(@Body request: VerifyEmailRequestDto): SessionResponseDto

    @POST("api/v1/auth/resend-verification")
    suspend fun resendVerification(@Body request: EmailOnlyRequestDto): AcceptedAuthRequestDto

    @POST("api/v1/auth/login")
    suspend fun loginWithPassword(@Body request: EmailLoginRequestDto): SessionResponseDto

    @POST("api/v1/auth/forgot-password")
    suspend fun forgotPassword(@Body request: EmailOnlyRequestDto): AcceptedAuthRequestDto

    @POST("api/v1/auth/reset-password")
    suspend fun resetPassword(@Body request: ResetPasswordRequestDto)

    @POST("api/v1/auth/change-password")
    suspend fun changePassword(@Body request: ChangePasswordRequestDto): SessionResponseDto

    @GET("api/v1/auth/me")
    suspend fun currentCustomer(): SessionUserDto

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

    @PUT("api/v1/users/{userId}/phone")
    suspend fun saveCustomerPhone(
        @Path("userId") userId: String,
        @Body request: SaveCustomerPhoneRequestDto,
    ): CustomerContactDto

}
