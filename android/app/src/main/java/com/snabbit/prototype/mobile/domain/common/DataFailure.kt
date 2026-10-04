package com.snabbit.prototype.mobile.domain.common

enum class DataFailureReason {
    UNAUTHORIZED,
    FORBIDDEN,
    NOT_FOUND,
    CONFLICT,
    VALIDATION,
    OFFLINE,
    SERVER,
    UNKNOWN,
}

class DataFailure(
    val reason: DataFailureReason,
    val detail: String? = null,
) : Exception(detail)
