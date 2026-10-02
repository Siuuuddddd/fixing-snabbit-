package com.snabbit.prototype.booking.domain

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

enum class BookingStatus {
    AWAITING_WORKER,
    AWAITING_PAYMENT,
    CONFIRMED,
    WORKER_UNAVAILABLE,
    EXPIRED,
    CANCELLED,
    COMPLETED,
}

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
            updatedAt = now
        }
    }

    fun complete(now: Instant) {
        check(status == BookingStatus.CONFIRMED) { "Only a confirmed booking can be completed" }
        status = BookingStatus.COMPLETED
        updatedAt = now
    }
}
