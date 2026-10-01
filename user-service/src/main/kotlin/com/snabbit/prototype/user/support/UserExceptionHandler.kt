package com.snabbit.prototype.user.support

import com.snabbit.prototype.user.application.FavouriteLimitReached
import com.snabbit.prototype.user.application.UserNotFound
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class UserExceptionHandler {
    @ExceptionHandler(UserNotFound::class)
    fun notFound(error: UserNotFound): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, error.message.orEmpty())

    @ExceptionHandler(FavouriteLimitReached::class)
    fun favouriteLimit(error: FavouriteLimitReached): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, error.message.orEmpty())
}
