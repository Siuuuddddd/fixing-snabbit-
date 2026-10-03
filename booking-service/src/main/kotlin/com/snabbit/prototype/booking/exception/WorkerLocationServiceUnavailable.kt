package com.snabbit.prototype.booking.exception

import org.springframework.stereotype.Service

class WorkerLocationServiceUnavailable(cause: Throwable) :
    RuntimeException("Worker location service is temporarily unavailable", cause)
