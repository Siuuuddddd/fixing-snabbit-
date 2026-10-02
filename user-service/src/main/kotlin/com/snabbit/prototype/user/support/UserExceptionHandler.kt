package com.snabbit.prototype.user.support

import com.snabbit.prototype.user.service.FavouriteLimitReached
import com.snabbit.prototype.user.service.UserNotFound
import com.snabbit.prototype.user.service.AddressNotFound
import com.snabbit.prototype.user.service.AddressAccessDenied
import com.snabbit.prototype.user.service.AddressRejected
import com.snabbit.prototype.user.auth.InvalidOtp
import com.snabbit.prototype.user.auth.OtpRateLimited
import com.snabbit.prototype.user.rating.CustomerRatingRejected
import com.snabbit.prototype.user.plus.PlusRequestRejected
import com.snabbit.prototype.user.plus.PlusPurchaseUnavailable
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

    @ExceptionHandler(AddressNotFound::class)
    fun addressNotFound(error: AddressNotFound): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, error.message.orEmpty())

    @ExceptionHandler(AddressAccessDenied::class)
    fun addressAccessDenied(error: AddressAccessDenied): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, error.message.orEmpty())

    @ExceptionHandler(AddressRejected::class)
    fun addressRejected(error: AddressRejected): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, error.message.orEmpty())

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

    @ExceptionHandler(PlusPurchaseUnavailable::class)
    fun plusPurchaseUnavailable(error: PlusPurchaseUnavailable): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, error.message.orEmpty())
}
