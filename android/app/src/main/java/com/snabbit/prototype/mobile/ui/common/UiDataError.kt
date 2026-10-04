package com.snabbit.prototype.mobile.ui.common

import com.snabbit.prototype.mobile.domain.common.DataFailure
import com.snabbit.prototype.mobile.domain.common.DataFailureReason

enum class UiDataErrorReason {
    UNAUTHORIZED,
    FORBIDDEN,
    NOT_FOUND,
    CONFLICT,
    VALIDATION,
    OFFLINE,
    SERVER,
    UNKNOWN,
}

data class UiDataError(
    val reason: UiDataErrorReason,
    val detail: String? = null,
)

fun Throwable.toUiDataError(): UiDataError {
    val failure = this as? DataFailure
    val reason = when (failure?.reason) {
        DataFailureReason.UNAUTHORIZED -> UiDataErrorReason.UNAUTHORIZED
        DataFailureReason.FORBIDDEN -> UiDataErrorReason.FORBIDDEN
        DataFailureReason.NOT_FOUND -> UiDataErrorReason.NOT_FOUND
        DataFailureReason.CONFLICT -> UiDataErrorReason.CONFLICT
        DataFailureReason.VALIDATION -> UiDataErrorReason.VALIDATION
        DataFailureReason.OFFLINE -> UiDataErrorReason.OFFLINE
        DataFailureReason.SERVER -> UiDataErrorReason.SERVER
        DataFailureReason.UNKNOWN, null -> UiDataErrorReason.UNKNOWN
    }
    return UiDataError(reason, failure?.detail)
}
