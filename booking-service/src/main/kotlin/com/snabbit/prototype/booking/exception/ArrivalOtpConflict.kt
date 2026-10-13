package com.snabbit.prototype.booking.exception

class ArrivalOtpConflict(
    val code: String,
    message: String,
    val retryAfterSeconds: Long? = null,
) : RuntimeException(message)

class ArrivalOtpAccessDenied : RuntimeException("This arrival challenge is not assigned to the authenticated worker")
