package com.snabbit.prototype.booking.payment

import com.snabbit.prototype.booking.dto.request.StartBookingPaymentCommand
import com.snabbit.prototype.booking.dto.response.BookingPaymentSnapshot
import com.snabbit.prototype.booking.exception.BookingRuleViolation
import com.snabbit.prototype.booking.exception.CustomerAccessDenied
import com.snabbit.prototype.booking.exception.InvalidBookingState
import com.snabbit.prototype.booking.service.BookingLifecycleService
import com.snabbit.prototype.booking.service.HomeContactService
import java.time.Clock
import java.time.Instant
import org.springframework.stereotype.Service

@Service
class BookingPaymentService(
    private val bookings: BookingLifecycleService,
    private val client: BookingPaymentClient,
    private val homeContacts: HomeContactService,
    private val clock: Clock,
) {
    fun start(command: StartBookingPaymentCommand): BookingPaymentSnapshot {
        homeContacts.ensureResolved(command.bookingId, command.authenticatedCustomerId)
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
        if (!bookings.hasCompleteWorkerSelection(command.bookingId, command.authenticatedCustomerId)) {
            throw BookingRuleViolation("WORKER_SELECTION_REQUIRED")
        }
        bookings.extendHoldsForPayment(command.bookingId)
        return try {
            client.start(command)
        } catch (error: RuntimeException) {
            runCatching { bookings.cancel(command.bookingId, command.authenticatedCustomerId) }
            throw error
        }
    }
}
