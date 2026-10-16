package com.snabbit.prototype.mobile.data.auth

import com.snabbit.prototype.mobile.data.auth.remote.email.ChangePasswordRequestDto
import com.snabbit.prototype.mobile.data.auth.remote.email.EmailAuthRemoteDataSource
import com.snabbit.prototype.mobile.data.auth.remote.email.EmailLoginRequestDto
import com.snabbit.prototype.mobile.data.auth.remote.email.EmailOnlyRequestDto
import com.snabbit.prototype.mobile.data.auth.remote.email.EmailRegisterRequestDto
import com.snabbit.prototype.mobile.data.auth.remote.email.ResetPasswordRequestDto
import com.snabbit.prototype.mobile.data.auth.remote.email.VerifyEmailRequestDto
import com.snabbit.prototype.mobile.data.network.ApiProblem
import com.snabbit.prototype.mobile.domain.auth.AuthDevice
import com.snabbit.prototype.mobile.domain.auth.AuthFailure
import com.snabbit.prototype.mobile.domain.auth.AuthFailureReason
import com.snabbit.prototype.mobile.domain.auth.AuthSession
import com.snabbit.prototype.mobile.domain.auth.EmailAuthRepository
import com.snabbit.prototype.mobile.domain.auth.EmailValidator
import com.snabbit.prototype.mobile.domain.auth.SessionStore
import com.snabbit.prototype.mobile.domain.auth.SessionUser
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import retrofit2.HttpException

class DefaultEmailAuthRepository(
    private val remote: EmailAuthRemoteDataSource,
    private val sessionStore: SessionStore,
    private val json: Json,
    private val nowEpochSeconds: () -> Long = { System.currentTimeMillis() / 1_000L },
) : EmailAuthRepository {
    override suspend fun verifyEmail(email: String, code: String, device: AuthDevice): AuthSession =
        verifyEmail(email, code, rememberMe = true, device = device)

    override suspend fun register(name: String, email: String, password: String, device: AuthDevice) = mapFailures {
        remote.register(EmailRegisterRequestDto(name.trim(), EmailValidator.normalize(email), password, true, device.id, device.name))
    }

    override suspend fun verifyEmail(email: String, code: String, rememberMe: Boolean, device: AuthDevice): AuthSession = mapFailures {
        store(remote.verifyEmail(VerifyEmailRequestDto(EmailValidator.normalize(email), code, device.id, device.name, rememberMe)))
    }

    override suspend fun resendVerification(email: String) = mapFailures {
        remote.resendVerification(EmailOnlyRequestDto(EmailValidator.normalize(email)))
    }

    override suspend fun login(email: String, password: String, device: AuthDevice): AuthSession =
        login(email, password, rememberMe = true, device = device)

    override suspend fun login(email: String, password: String, rememberMe: Boolean, device: AuthDevice): AuthSession = mapFailures {
        store(remote.login(EmailLoginRequestDto(EmailValidator.normalize(email), password, device.id, device.name, rememberMe)))
    }

    override suspend fun forgotPassword(email: String) = mapFailures {
        remote.forgotPassword(EmailOnlyRequestDto(EmailValidator.normalize(email)))
    }

    override suspend fun resetPassword(email: String, code: String, newPassword: String) = mapFailures {
        remote.resetPassword(ResetPasswordRequestDto(EmailValidator.normalize(email), code, newPassword))
        sessionStore.clear()
    }

    override suspend fun changePassword(currentPassword: String, newPassword: String): AuthSession = mapFailures {
        store(remote.changePassword(ChangePasswordRequestDto(currentPassword, newPassword)))
    }

    override suspend fun currentCustomer(): SessionUser = mapFailures {
        val current = sessionStore.current()
        val user = remote.currentCustomer().toDomain(current?.user?.phoneNumber)
        if (current != null) sessionStore.save(current.copy(user = user))
        user
    }

    private fun store(response: com.snabbit.prototype.mobile.data.auth.remote.SessionResponseDto): AuthSession {
        val phone = response.user.phoneNumber
            ?: sessionStore.current()?.user?.takeIf { it.id == response.user.id }?.phoneNumber
        return response.toDomain(nowEpochSeconds(), phone).also(sessionStore::save)
    }

    private suspend fun <T> mapFailures(block: suspend () -> T): T = try {
        block()
    } catch (failure: AuthFailure) {
        throw failure
    } catch (failure: IOException) {
        throw AuthFailure(AuthFailureReason.OFFLINE)
    } catch (failure: HttpException) {
        val problem = failure.response()?.errorBody()?.string()?.let(::problemDetail)
        throw AuthFailure(
            reason = problem.toReason(failure.code()),
            message = problem?.detail,
            retryAfterSeconds = problem?.retryAfterSeconds,
            fieldErrors = problem?.fields.orEmpty(),
            passwordReason = problem?.reason,
        )
    } catch (failure: CancellationException) {
        throw failure
    } catch (_: Exception) {
        throw AuthFailure(AuthFailureReason.UNKNOWN)
    }

    private fun problemDetail(body: String): ApiProblem? = runCatching { json.decodeFromString<ApiProblem>(body) }.getOrNull()

    private fun ApiProblem?.toReason(status: Int): AuthFailureReason = when (this?.code ?: this?.errorCode) {
        "INVALID_CREDENTIALS" -> AuthFailureReason.INVALID_CREDENTIALS
        "EMAIL_NOT_VERIFIED" -> AuthFailureReason.EMAIL_NOT_VERIFIED
        "WEAK_PASSWORD" -> AuthFailureReason.WEAK_PASSWORD
        "INVALID_OR_EXPIRED_CODE" -> AuthFailureReason.INVALID_OR_EXPIRED_CODE
        "TOO_MANY_ATTEMPTS" -> AuthFailureReason.TOO_MANY_ATTEMPTS
        "RATE_LIMITED" -> AuthFailureReason.RATE_LIMITED
        "TERMS_NOT_ACCEPTED" -> AuthFailureReason.TERMS_NOT_ACCEPTED
        "ACCOUNT_DISABLED" -> AuthFailureReason.ACCOUNT_DISABLED
        "PASSWORD_NOT_SET" -> AuthFailureReason.PASSWORD_NOT_SET
        "VALIDATION_ERROR" -> AuthFailureReason.VALIDATION_ERROR
        "SESSION_REVOKED" -> AuthFailureReason.SESSION_REVOKED
        else -> when (status) {
            429 -> AuthFailureReason.RATE_LIMITED
            in 500..599 -> AuthFailureReason.SERVER
            else -> AuthFailureReason.UNKNOWN
        }
    }
}

