package com.snabbit.prototype.worker.exception

import org.springframework.http.HttpStatus

class WorkerRatingRejected(
    message: String,
    val errorCode: String = "RATING_REJECTED",
    val status: HttpStatus = HttpStatus.CONFLICT,
) : RuntimeException(message)
