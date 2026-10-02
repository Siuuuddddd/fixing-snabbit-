package com.snabbit.prototype.user.support

import com.snabbit.prototype.user.application.FavouriteLimitReached
import com.snabbit.prototype.user.application.UserNotFound
import com.snabbit.prototype.user.auth.InvalidOtp
import com.snabbit.prototype.user.auth.OtpRateLimited
import com.snabbit.prototype.user.rating.CustomerRatingRejected
import com.snabbit.prototype.user.plus.PlusRequestRejected
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

class CustomerAccessDenied : RuntimeException("Authenticated customer does not own this resource")

@RestControllerAdvice
class UserExceptionHandler {
    @ExceptionHandler(UserNotFound::class)
    fun notFound(error: UserNotFound): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, error.message.orEmpty())

    @ExceptionHandler(FavouriteLimitReached::class)
    fun favouriteLimit(error: FavouriteLimitReached): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, error.message.orEmpty())

    @ExceptionHandler(InvalidOtp::class)
    fun invalidOtp(error: InvalidOtp): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, error.message.orEmpty())

    @ExceptionHandler(OtpRateLimited::class)
    fun otpRateLimited(error: OtpRateLimited): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, error.message.orEmpty())

    @ExceptionHandler(IllegalArgumentException::class)
    fun invalidArgument(error: IllegalArgumentException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, error.message.orEmpty())

    @ExceptionHandler(CustomerAccessDenied::class)
    fun accessDenied(error: CustomerAccessDenied): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, error.message.orEmpty())

    @ExceptionHandler(CustomerRatingRejected::class)
    fun ratingRejected(error: CustomerRatingRejected): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, error.message.orEmpty())

    @ExceptionHandler(PlusRequestRejected::class)
    fun plusRejected(error: PlusRequestRejected): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, error.message.orEmpty())
}
