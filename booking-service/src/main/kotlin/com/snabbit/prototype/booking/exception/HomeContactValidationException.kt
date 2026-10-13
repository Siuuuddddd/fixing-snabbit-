package com.snabbit.prototype.booking.exception

class HomeContactValidationException(
    val field: String,
    message: String,
) : RuntimeException(message)
