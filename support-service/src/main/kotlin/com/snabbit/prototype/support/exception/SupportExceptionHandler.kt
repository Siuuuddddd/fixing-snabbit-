package com.snabbit.prototype.support.exception

import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class SupportExceptionHandler {
    @ExceptionHandler(SupportResourceNotFound::class)
    fun notFound(error: SupportResourceNotFound): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, error.message.orEmpty())

    @ExceptionHandler(SupportRequestRejected::class)
    fun rejected(error: SupportRequestRejected): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, error.message.orEmpty())
}
