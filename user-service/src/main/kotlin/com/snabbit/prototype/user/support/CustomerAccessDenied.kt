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
import com.snabbit.prototype.user.location.LocationVerificationRejected
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

class CustomerAccessDenied : RuntimeException("Authenticated customer does not own this resource")
