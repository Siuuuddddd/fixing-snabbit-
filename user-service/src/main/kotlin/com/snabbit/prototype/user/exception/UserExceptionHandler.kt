package com.snabbit.prototype.user.exception

import com.snabbit.prototype.platform.UnverifiedTrialRecipient
import com.snabbit.prototype.platform.VerificationProviderUnavailable
import com.snabbit.prototype.platform.GoogleIdentityFailure
import com.snabbit.prototype.platform.GoogleIdentityVerificationException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class UserExceptionHandler {
    @ExceptionHandler(GoogleIdentityVerificationException::class)
    fun invalidGoogleIdentity(error: GoogleIdentityVerificationException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Google sign-in could not be verified").also {
            it.setProperty(
                "code",
                if (error.failure == GoogleIdentityFailure.EMAIL_NOT_VERIFIED) "EMAIL_NOT_VERIFIED" else "INVALID_GOOGLE_TOKEN",
            )
        }

    @ExceptionHandler(SessionRevoked::class)
    fun sessionRevoked(error: SessionRevoked): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, error.message.orEmpty()).also {
            it.setProperty("code", "SESSION_REVOKED")
        }

    @ExceptionHandler(RefreshInProgress::class)
    fun refreshInProgress(error: RefreshInProgress): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, error.message.orEmpty()).also {
            it.setProperty("code", "REFRESH_IN_PROGRESS")
            it.setProperty("retryable", true)
        }

    @ExceptionHandler(AuthRateLimited::class)
    fun authRateLimited(error: AuthRateLimited): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, error.message.orEmpty()).also {
            it.setProperty("code", "RATE_LIMITED")
        }

    @ExceptionHandler(CustomerAccountDisabled::class)
    fun accountDisabled(error: CustomerAccountDisabled): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, error.message.orEmpty()).also {
            it.setProperty("code", "ACCOUNT_DISABLED")
        }

    @ExceptionHandler(CustomerAccountConflict::class)
    fun accountConflict(error: CustomerAccountConflict): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, error.message.orEmpty()).also {
            it.setProperty("code", "ACCOUNT_LINK_CONFLICT")
        }

    @ExceptionHandler(UnverifiedTrialRecipient::class)
    fun unverifiedTrial(error: UnverifiedTrialRecipient): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, error.message.orEmpty()).also {
            it.setProperty("errorCode", "TWILIO_TRIAL_NUMBER_UNVERIFIED")
        }

    @ExceptionHandler(VerificationProviderUnavailable::class)
    fun providerUnavailable(error: VerificationProviderUnavailable): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, error.message.orEmpty()).also {
            it.setProperty("errorCode", "OTP_PROVIDER_UNAVAILABLE")
        }
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

    @ExceptionHandler(LocationVerificationRejected::class)
    fun locationRejected(error: LocationVerificationRejected): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, error.message.orEmpty())

    @ExceptionHandler(FavouriteLimitReached::class)
    fun favouriteLimit(error: FavouriteLimitReached): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, error.message.orEmpty()).also {
            it.setProperty("code", "FAVOURITE_LIMIT_REACHED")
        }

    @ExceptionHandler(PlusRequired::class)
    fun plusRequired(error: PlusRequired): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, error.message.orEmpty()).also {
            it.setProperty("code", "PLUS_REQUIRED")
        }

    @ExceptionHandler(NotEligibleFavourite::class)
    fun notEligibleFavourite(error: NotEligibleFavourite): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, error.message.orEmpty()).also {
            it.setProperty("code", "NOT_ELIGIBLE_FAVOURITE")
        }

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
