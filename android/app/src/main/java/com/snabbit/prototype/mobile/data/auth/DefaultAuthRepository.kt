package com.snabbit.prototype.mobile.data.auth

import com.snabbit.prototype.mobile.data.auth.remote.AuthRemoteDataSource
import com.snabbit.prototype.mobile.data.network.ApiProblem
import com.snabbit.prototype.mobile.domain.auth.AuthFailure
import com.snabbit.prototype.mobile.domain.auth.AuthFailureReason
import com.snabbit.prototype.mobile.domain.auth.AuthRepository
import com.snabbit.prototype.mobile.domain.auth.AuthResult
import com.snabbit.prototype.mobile.domain.auth.OtpChallenge
import com.snabbit.prototype.mobile.navigation.AppRole
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import retrofit2.HttpException

class DefaultAuthRepository(
    private val remoteDataSource: AuthRemoteDataSource,
    private val tokenStore: TokenStore,
    private val role: AppRole,
    private val json: Json,
) : AuthRepository {
    override suspend fun requestOtp(phoneNumber: String): OtpChallenge = mapFailures {
        val response = remoteDataSource.requestOtp(role, phoneNumber)
        OtpChallenge(expiresInSeconds = response.expiresInSeconds)
    }

    override suspend fun verifyOtp(phoneNumber: String, otp: String): AuthResult = mapFailures {
            val response = remoteDataSource.verifyOtp(role, phoneNumber, otp)
            if (response.status == "REGISTRATION_REQUIRED") {
                AuthResult.RegistrationRequired(requireNotNull(response.registrationToken))
            } else {
                tokenStore.saveAccessToken(requireNotNull(response.accessToken))
                AuthResult.Authenticated
            }
    }

    override suspend fun register(phoneNumber: String, displayName: String, registrationToken: String) {
        mapFailures {
            val response = remoteDataSource.registerCustomer(phoneNumber, displayName, registrationToken)
            tokenStore.saveAccessToken(requireNotNull(response.accessToken))
        }
    }

    private suspend fun <T> mapFailures(block: suspend () -> T): T = try {
        block()
    } catch (failure: AuthFailure) {
        throw failure
    } catch (failure: IOException) {
        throw AuthFailure(AuthFailureReason.OFFLINE, failure.message)
    } catch (failure: HttpException) {
        val problem = failure.response()?.errorBody()?.string()?.let(::problemDetail)
        val reason = when (failure.code()) {
            401 -> AuthFailureReason.INVALID_OR_EXPIRED_OTP
            429 -> AuthFailureReason.RATE_LIMITED
            in 500..599 -> AuthFailureReason.SERVER
            else -> if (problem?.errorCode == "WORKER_PROFILE_REQUIRED") AuthFailureReason.WORKER_PROFILE_REQUIRED else AuthFailureReason.UNKNOWN
        }
        throw AuthFailure(reason, problem?.detail)
    } catch (failure: CancellationException) {
        throw failure
    } catch (failure: Exception) {
        throw AuthFailure(AuthFailureReason.UNKNOWN, failure.message)
    }

    private fun problemDetail(body: String): ApiProblem? = runCatching {
        json.decodeFromString<ApiProblem>(body)
    }.getOrNull()
}
