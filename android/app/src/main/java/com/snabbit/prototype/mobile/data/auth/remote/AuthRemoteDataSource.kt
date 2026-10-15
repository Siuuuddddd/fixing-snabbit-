package com.snabbit.prototype.mobile.data.auth.remote

import com.snabbit.prototype.mobile.navigation.AppRole
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement

sealed interface RemoteGoogleSignInResult {
    data class Session(val value: SessionResponseDto) : RemoteGoogleSignInResult
    data class Picker(val value: WorkerPickerResponseDto) : RemoteGoogleSignInResult
}

interface AuthRemoteDataSource {
    suspend fun signInWithGoogle(role: AppRole, request: GoogleSignInRequestDto): RemoteGoogleSignInResult =
        error("Google sign-in is not implemented")
    suspend fun selectWorkerProfile(request: WorkerSelectProfileRequestDto): SessionResponseDto =
        error("Worker selection is not implemented")
    suspend fun switchWorkerProfile(workerId: String): SessionResponseDto = error("Worker switching is not implemented")
    suspend fun refresh(role: AppRole, refreshToken: String): SessionResponseDto = error("Session refresh is not implemented")
    suspend fun logout(role: AppRole, allDevices: Boolean) = Unit
    suspend fun saveCustomerPhone(userId: String, phoneNumber: String): CustomerContactDto =
        error("Phone update is not implemented")
    suspend fun requestOtp(role: AppRole, phoneNumber: String): OtpChallengeDto
    suspend fun verifyOtp(role: AppRole, phoneNumber: String, otp: String): AccessTokenDto
    suspend fun registerCustomer(phoneNumber: String, displayName: String, registrationToken: String): AccessTokenDto
}

class RetrofitAuthRemoteDataSource(
    private val api: AuthApi,
    private val json: Json,
) : AuthRemoteDataSource {
    override suspend fun signInWithGoogle(role: AppRole, request: GoogleSignInRequestDto): RemoteGoogleSignInResult =
        when (role) {
            AppRole.CUSTOMER -> RemoteGoogleSignInResult.Session(api.googleCustomer(request))
            AppRole.WORKER -> {
                val response = api.googleWorker(request)
                if (response["requiresWorkerSelection"]?.toString() == "true") {
                    RemoteGoogleSignInResult.Picker(json.decodeFromJsonElement<WorkerPickerResponseDto>(response))
                } else {
                    RemoteGoogleSignInResult.Session(json.decodeFromJsonElement<SessionResponseDto>(response))
                }
            }
        }

    override suspend fun selectWorkerProfile(request: WorkerSelectProfileRequestDto) = api.selectWorkerProfile(request)
    override suspend fun switchWorkerProfile(workerId: String) = api.switchWorkerProfile(WorkerSwitchProfileRequestDto(workerId))
    override suspend fun refresh(role: AppRole, refreshToken: String) = when (role) {
        AppRole.CUSTOMER -> api.refreshCustomer(RefreshSessionRequestDto(refreshToken))
        AppRole.WORKER -> api.refreshWorker(RefreshSessionRequestDto(refreshToken))
    }

    override suspend fun logout(role: AppRole, allDevices: Boolean) {
        when (role) {
            AppRole.CUSTOMER -> if (allDevices) api.logoutAllCustomers() else api.logoutCustomer()
            AppRole.WORKER -> if (allDevices) api.logoutAllWorkers() else api.logoutWorker()
        }
    }

    override suspend fun saveCustomerPhone(userId: String, phoneNumber: String) =
        api.saveCustomerPhone(userId, SaveCustomerPhoneRequestDto(phoneNumber))

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

    override suspend fun registerCustomer(phoneNumber: String, displayName: String, registrationToken: String) =
        api.registerCustomer(CustomerRegistrationDto(phoneNumber, displayName, registrationToken))
}
