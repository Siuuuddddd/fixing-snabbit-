package com.snabbit.prototype.booking.domain

import com.snabbit.prototype.booking.domain.enums.BookingType

import com.snabbit.prototype.booking.domain.enums.BookingStatus

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.math.BigDecimal
import java.util.UUID

@Entity
@Table(name = "booking")
class Booking(
    @Id
    val id: UUID,
    @Column(name = "customer_id", nullable = false)
    val customerId: UUID,
    @Column(name = "service_id", nullable = false)
    val serviceId: UUID,
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "booking_type", nullable = false, columnDefinition = "booking_type")
    val bookingType: BookingType,
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "booking_status")
    var status: BookingStatus,
    @Column(name = "duration_minutes", nullable = false)
    val durationMinutes: Int,
    @Column(name = "quoted_total_minor", nullable = false)
    val quotedTotalMinor: Long = 0,
    @Column(name = "service_latitude")
    val serviceLatitude: BigDecimal? = null,
    @Column(name = "service_longitude")
    val serviceLongitude: BigDecimal? = null,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    val requirements: BookingRequirements = BookingRequirements(),
    @Column(name = "payment_reference", unique = true)
    var paymentReference: String? = null,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant,
    @Column(name = "gross_total_minor", nullable = false)
    val grossTotalMinor: Long = quotedTotalMinor,
    @Column(name = "plus_discount_minor", nullable = false)
    val plusDiscountMinor: Long = 0,
    @Column(name = "plus_pass_id")
    val plusPassId: UUID? = null,
    @Column(name = "confirmed_at")
    var confirmedAt: Instant? = null,
    @Column(name = "cancellation_type")
    var cancellationType: String? = null,
    @Column(name = "cancellation_reason")
    var cancellationReason: String? = null,
    @Column(name = "cancellation_fee_minor")
    var cancellationFeeMinor: Long? = null,
    @Column(name = "refund_amount_minor")
    var refundAmountMinor: Long? = null,
    @Column(name = "cancelled_at")
    var cancelledAt: Instant? = null,
    @Column(name = "cancelled_by_staff_id")
    var cancelledByStaffId: UUID? = null,
    @Column(name = "service_address_id")
    val serviceAddressId: UUID? = null,
    @Column(name = "service_address_label")
    val serviceAddressLabel: String? = null,
    @Column(name = "service_address_line")
    val serviceAddressLine: String? = null,
    @Column(name = "service_address_pincode")
    val serviceAddressPincode: String? = null,
    @Column(name = "idempotency_key", length = 120)
    val idempotencyKey: String? = null,
    @Column(name = "request_fingerprint", length = 64)
    val requestFingerprint: String? = null,
    @Version
    var version: Long = 0,
) {
    fun awaitPayment(now: Instant) {
        check(status == BookingStatus.AWAITING_WORKER) { "Booking is not awaiting worker acceptance" }
        status = BookingStatus.AWAITING_PAYMENT
        updatedAt = now
    }

    fun confirm(reference: String, now: Instant) {
        check(status == BookingStatus.AWAITING_PAYMENT) { "Booking is not ready for payment confirmation" }
        paymentReference = reference
        confirmedAt = now
        status = BookingStatus.CONFIRMED
        updatedAt = now
    }

    fun markWorkerUnavailable(now: Instant) {
        if (status == BookingStatus.AWAITING_WORKER) {
            status = BookingStatus.WORKER_UNAVAILABLE
            updatedAt = now
        }
    }

    fun expire(now: Instant) {
        if (status == BookingStatus.AWAITING_WORKER || status == BookingStatus.AWAITING_PAYMENT) {
            status = BookingStatus.EXPIRED
            updatedAt = now
        }
    }

    fun cancel(now: Instant) {
        check(status != BookingStatus.CONFIRMED && status != BookingStatus.COMPLETED) {
            "Confirmed or completed bookings require the cancellation/refund workflow"
        }
        if (status != BookingStatus.CANCELLED) {
            status = BookingStatus.CANCELLED
            cancellationType = "PRE_CONFIRMATION"
            cancelledAt = now
            updatedAt = now
        }
    }

    fun cancelConfirmed(reason: String?, feeMinor: Long, refundMinor: Long, now: Instant) {
        check(status == BookingStatus.CONFIRMED) { "Only a confirmed booking can use the refund cancellation workflow" }
        require(feeMinor >= 0 && refundMinor >= 0 && feeMinor + refundMinor == quotedTotalMinor) {
            "Cancellation money must equal the charged quote"
        }
        status = BookingStatus.CANCELLED
        cancellationType = "CUSTOMER"
        cancellationReason = reason
        cancellationFeeMinor = feeMinor
        refundAmountMinor = refundMinor
        cancelledAt = now
        updatedAt = now
    }

    fun cancelByPlatform(reason: String, staffId: UUID?, now: Instant) {
        check(status == BookingStatus.CONFIRMED) { "Only a confirmed booking can be cancelled by the platform" }
        require(reason.isNotBlank()) { "Platform cancellation reason is required" }
        status = BookingStatus.CANCELLED
        cancellationType = "PLATFORM"
        cancellationReason = reason
        cancellationFeeMinor = 0
        refundAmountMinor = quotedTotalMinor
        cancelledByStaffId = staffId
        cancelledAt = now
        updatedAt = now
    }

    fun complete(now: Instant) {
        check(status == BookingStatus.CONFIRMED) { "Only a confirmed booking can be completed" }
        status = BookingStatus.COMPLETED
        updatedAt = now
    }
}
