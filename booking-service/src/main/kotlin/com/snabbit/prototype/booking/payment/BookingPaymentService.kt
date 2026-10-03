package com.snabbit.prototype.booking.payment

import com.snabbit.prototype.booking.dto.request.StartBookingPaymentCommand
import com.snabbit.prototype.booking.dto.response.BookingPaymentSnapshot
import com.snabbit.prototype.booking.exception.BookingRuleViolation
import com.snabbit.prototype.booking.exception.CustomerAccessDenied
import com.snabbit.prototype.booking.exception.InvalidBookingState
import com.snabbit.prototype.booking.service.BookingLifecycleService
import java.time.Clock
import java.time.Instant
import org.springframework.stereotype.Service

@Service
class BookingPaymentService(
    private val bookings: BookingLifecycleService,
    private val client: BookingPaymentClient,
    private val clock: Clock,
) {
    fun start(command: StartBookingPaymentCommand): BookingPaymentSnapshot {
        val quote = bookings.paymentQuote(command.bookingId)
        if (quote.customerId != command.authenticatedCustomerId) {
            throw com.snabbit.prototype.booking.exception.CustomerAccessDenied()
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
