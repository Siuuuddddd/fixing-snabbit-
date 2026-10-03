package com.snabbit.prototype.notification.exception

import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class NotificationExceptionHandler {
    @ExceptionHandler(NotificationNotFound::class)
    fun notFound(error: NotificationNotFound): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, error.message.orEmpty())

    @ExceptionHandler(NotificationAccessDenied::class)
    fun forbidden(error: NotificationAccessDenied): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, error.message.orEmpty())

    @ExceptionHandler(NotificationRequestRejected::class)
    fun rejected(error: NotificationRequestRejected): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, error.message.orEmpty())
}
