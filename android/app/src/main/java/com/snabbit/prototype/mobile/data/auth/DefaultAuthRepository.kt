package com.snabbit.prototype.mobile.data.auth

import com.snabbit.prototype.mobile.data.auth.remote.AuthRemoteDataSource
import com.snabbit.prototype.mobile.data.auth.remote.GoogleSignInRequestDto
import com.snabbit.prototype.mobile.data.auth.remote.RemoteGoogleSignInResult
import com.snabbit.prototype.mobile.data.auth.remote.SessionResponseDto
import com.snabbit.prototype.mobile.data.auth.remote.WorkerSelectProfileRequestDto
import com.snabbit.prototype.mobile.data.network.ApiProblem
import com.snabbit.prototype.mobile.domain.auth.AuthFailure
import com.snabbit.prototype.mobile.domain.auth.AuthFailureReason
import com.snabbit.prototype.mobile.domain.auth.AuthRepository
import com.snabbit.prototype.mobile.domain.auth.AuthResult
import com.snabbit.prototype.mobile.domain.auth.AuthDevice
import com.snabbit.prototype.mobile.domain.auth.AuthSession
import com.snabbit.prototype.mobile.domain.auth.GoogleIdToken
import com.snabbit.prototype.mobile.domain.auth.GoogleSignInResult
import com.snabbit.prototype.mobile.domain.auth.OtpChallenge
import com.snabbit.prototype.mobile.domain.auth.SessionStore
import com.snabbit.prototype.mobile.domain.auth.SessionUser
import com.snabbit.prototype.mobile.domain.auth.WorkerPickerOption
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
    private val sessionStore: SessionStore? = null,
) : AuthRepository {
    override suspend fun signInWithGoogle(credential: GoogleIdToken, device: AuthDevice): GoogleSignInResult = mapFailures {
        val request = GoogleSignInRequestDto(
            credential.value,
            credential.nonce,
            device.id,
            device.name,
        )
        when (val result = remoteDataSource.signInWithGoogle(role, request)) {
            is RemoteGoogleSignInResult.Session -> GoogleSignInResult.Authenticated(store(result.value))
            is RemoteGoogleSignInResult.Picker -> GoogleSignInResult.WorkerSelectionRequired(
                result.value.selectionToken,
                nowEpochSeconds() + result.value.expiresIn,
                result.value.workers.map { WorkerPickerOption(it.workerId, it.name, it.tier) },
            )
        }
    }

    override suspend fun selectWorkerProfile(
        credential: GoogleIdToken,
        selectionToken: String,
        workerId: String,
        device: AuthDevice,
    ): AuthSession = mapFailures {
        store(
            remoteDataSource.selectWorkerProfile(
                WorkerSelectProfileRequestDto(
                    credential.value,
                    credential.nonce,
                    selectionToken,
                    workerId,
                    device.id,
                    device.name,
                ),
            ),
        )
    }

    override suspend fun switchWorkerProfile(workerId: String): AuthSession = mapFailures {
        store(remoteDataSource.switchWorkerProfile(workerId))
    }

    override suspend fun refreshSession(): AuthSession = mapFailures {
        val refreshToken = sessionStore?.current()?.refreshToken
            ?: throw AuthFailure(AuthFailureReason.SESSION_REVOKED)
        store(remoteDataSource.refresh(role, refreshToken))
    }

    override suspend fun signOut(allDevices: Boolean) {
        try {
            mapFailures { remoteDataSource.logout(role, allDevices) }
        } finally {
            sessionStore?.clear()
            tokenStore.clear()
        }
    }

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
        val reason = when (problem?.code ?: problem?.errorCode) {
            "INVALID_GOOGLE_TOKEN" -> AuthFailureReason.INVALID_GOOGLE_TOKEN
            "EMAIL_NOT_VERIFIED" -> AuthFailureReason.EMAIL_NOT_VERIFIED
            "WORKER_NOT_REGISTERED" -> AuthFailureReason.WORKER_NOT_REGISTERED
            "INVALID_SELECTION_TOKEN" -> AuthFailureReason.INVALID_SELECTION_TOKEN
            "ACCOUNT_DISABLED" -> AuthFailureReason.ACCOUNT_DISABLED
            "ACCOUNT_LINK_CONFLICT" -> AuthFailureReason.ACCOUNT_LINK_CONFLICT
            "SESSION_REVOKED" -> AuthFailureReason.SESSION_REVOKED
            "REFRESH_IN_PROGRESS" -> AuthFailureReason.REFRESH_IN_PROGRESS
            "RATE_LIMITED" -> AuthFailureReason.RATE_LIMITED
            "PHONE_REQUIRED" -> AuthFailureReason.PHONE_REQUIRED
            "SHARED_WORKER_SESSION_REQUIRED" -> AuthFailureReason.SHARED_WORKER_SESSION_REQUIRED
            else -> when (failure.code()) {
            401 -> AuthFailureReason.INVALID_OR_EXPIRED_OTP
            429 -> AuthFailureReason.RATE_LIMITED
            in 500..599 -> AuthFailureReason.SERVER
            else -> if (problem?.errorCode == "WORKER_PROFILE_REQUIRED") AuthFailureReason.WORKER_PROFILE_REQUIRED else AuthFailureReason.UNKNOWN
            }
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

    private fun store(response: SessionResponseDto): AuthSession {
        val session = AuthSession(
            response.accessToken,
            response.tokenType,
            nowEpochSeconds() + response.expiresIn,
            response.refreshToken,
            SessionUser(
                response.user.id,
                response.user.name,
                response.user.email,
                response.user.pictureUrl,
                response.user.isNewUser,
                response.user.role,
            ),
        )
        requireNotNull(sessionStore) { "SessionStore is required for Google authentication" }.save(session)
        tokenStore.saveAccessToken(session.accessToken)
        return session
    }

    private fun nowEpochSeconds(): Long = System.currentTimeMillis() / 1_000L
}
