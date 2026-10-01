package com.snabbit.prototype.wallet.support

import com.snabbit.prototype.wallet.payment.PaymentProviderException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class WalletExceptionHandler {
    @ExceptionHandler(WalletNotFound::class, PaymentNotFound::class)
    fun notFound(error: RuntimeException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, error.message.orEmpty())

    @ExceptionHandler(WalletRuleViolation::class)
    fun ruleViolation(error: WalletRuleViolation): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, error.message.orEmpty())

    @ExceptionHandler(PaymentConflict::class)
    fun conflict(error: PaymentConflict): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, error.message.orEmpty())

    @ExceptionHandler(InvalidWebhook::class)
    fun invalidWebhook(error: InvalidWebhook): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, error.message.orEmpty())

    @ExceptionHandler(PaymentProviderException::class)
    fun providerFailure(error: PaymentProviderException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, error.message.orEmpty())

    @ExceptionHandler(BookingQuoteUnavailable::class)
    fun quoteUnavailable(error: BookingQuoteUnavailable): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, error.message.orEmpty())
}
