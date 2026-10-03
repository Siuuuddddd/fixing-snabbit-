package com.snabbit.prototype.booking.dto.request

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

data class StartBookingPaymentCommand(
    val walletId: UUID,
    val bookingId: UUID,
    val authenticatedCustomerId: UUID,
    val useWallet: Boolean,
    val idempotencyKey: String,
)
