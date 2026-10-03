package com.snabbit.prototype.booking.payment

import com.snabbit.prototype.booking.dto.response.BookingPaymentSnapshot

import com.snabbit.prototype.booking.dto.request.StartBookingPaymentCommand

import com.snabbit.prototype.booking.service.BookingLifecycleService
import com.snabbit.prototype.booking.config.IntegrationProperties
import com.snabbit.prototype.booking.security.InternalServiceClientFactory
import com.snabbit.prototype.booking.support.BookingRuleViolation
import com.snabbit.prototype.booking.support.InvalidBookingState
import com.snabbit.prototype.booking.support.PaymentRequestRejected
import com.snabbit.prototype.booking.support.PaymentServiceUnavailable
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
import java.time.Clock
import java.time.Instant
import java.util.UUID

@Service
class BookingPaymentService(
    private val bookings: BookingLifecycleService,
    private val client: BookingPaymentClient,
    private val clock: Clock,
) {
    fun start(command: StartBookingPaymentCommand): BookingPaymentSnapshot {
        val quote = bookings.paymentQuote(command.bookingId)
        if (quote.customerId != command.authenticatedCustomerId) {
            throw com.snabbit.prototype.booking.support.CustomerAccessDenied()
        }
        if (quote.status != "AWAITING_PAYMENT") {
            throw InvalidBookingState("Booking is not awaiting payment")
        }
        if (!quote.holdExpiresAt.isAfter(clock.instant())) {
            throw InvalidBookingState("Booking slot hold has expired")
        }
        if (quote.quotedTotalMinor <= 0 || quote.currency != "INR") {
            throw BookingRuleViolation("Booking has no valid INR payment quote")
        }
        return client.start(command)
    }
}
