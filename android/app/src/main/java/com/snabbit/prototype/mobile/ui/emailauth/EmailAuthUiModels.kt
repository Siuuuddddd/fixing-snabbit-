package com.snabbit.prototype.mobile.ui.emailauth

import com.snabbit.prototype.mobile.domain.auth.AuthFailure
import com.snabbit.prototype.mobile.domain.auth.AuthFailureReason

enum class EmailAuthUiError {
    INVALID_CREDENTIALS,
    EMAIL_NOT_VERIFIED,
    WEAK_PASSWORD,
    INVALID_OR_EXPIRED_CODE,
    TOO_MANY_ATTEMPTS,
    RATE_LIMITED,
    TERMS_NOT_ACCEPTED,
    ACCOUNT_DISABLED,
    PASSWORD_NOT_SET,
    VALIDATION_ERROR,
    SESSION_REVOKED,
    OFFLINE,
    UNEXPECTED,
}

data class EmailAuthError(
    val type: EmailAuthUiError,
    val retryAfterSeconds: Long? = null,
    val fields: Map<String, String> = emptyMap(),
)

internal fun Throwable.toEmailAuthError(): EmailAuthError {
    val failure = this as? AuthFailure
    val type = when (failure?.reason) {
        AuthFailureReason.INVALID_CREDENTIALS -> EmailAuthUiError.INVALID_CREDENTIALS
        AuthFailureReason.EMAIL_NOT_VERIFIED -> EmailAuthUiError.EMAIL_NOT_VERIFIED
        AuthFailureReason.WEAK_PASSWORD -> EmailAuthUiError.WEAK_PASSWORD
        AuthFailureReason.INVALID_OR_EXPIRED_CODE -> EmailAuthUiError.INVALID_OR_EXPIRED_CODE
        AuthFailureReason.TOO_MANY_ATTEMPTS -> EmailAuthUiError.TOO_MANY_ATTEMPTS
        AuthFailureReason.RATE_LIMITED -> EmailAuthUiError.RATE_LIMITED
        AuthFailureReason.TERMS_NOT_ACCEPTED -> EmailAuthUiError.TERMS_NOT_ACCEPTED
        AuthFailureReason.ACCOUNT_DISABLED -> EmailAuthUiError.ACCOUNT_DISABLED
        AuthFailureReason.PASSWORD_NOT_SET -> EmailAuthUiError.PASSWORD_NOT_SET
        AuthFailureReason.VALIDATION_ERROR -> EmailAuthUiError.VALIDATION_ERROR
        AuthFailureReason.SESSION_REVOKED -> EmailAuthUiError.SESSION_REVOKED
        AuthFailureReason.OFFLINE -> EmailAuthUiError.OFFLINE
        else -> EmailAuthUiError.UNEXPECTED
    }
    return EmailAuthError(type, failure?.retryAfterSeconds, failure?.fieldErrors.orEmpty())
}

