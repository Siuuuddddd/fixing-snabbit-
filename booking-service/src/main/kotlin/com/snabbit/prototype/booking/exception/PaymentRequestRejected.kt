package com.snabbit.prototype.booking.exception

import org.springframework.http.HttpStatusCode

class PaymentRequestRejected(val status: HttpStatusCode, message: String) : RuntimeException(message)
