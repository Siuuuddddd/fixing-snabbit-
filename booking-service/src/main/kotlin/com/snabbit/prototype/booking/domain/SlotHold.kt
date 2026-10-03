package com.snabbit.prototype.booking.domain

import com.snabbit.prototype.booking.domain.enums.JobStatus
import com.snabbit.prototype.booking.domain.enums.SlotHoldStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Enumerated
import jakarta.persistence.EnumType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

@Entity
@Table(name = "slot_hold")
class SlotHold(
    @Id
    val id: UUID,
    @Column(name = "booking_id", nullable = false)
    val bookingId: UUID,
    @Column(name = "visit_index", nullable = false)
    val visitIndex: Int,
    @Column(name = "customer_id", nullable = false)
    val customerId: UUID,
    @Column(name = "worker_id", nullable = false)
    val workerId: UUID,
    @Column(name = "service_id", nullable = false)
    val serviceId: UUID,
    @Column(name = "starts_at", nullable = false)
    val startsAt: Instant,
    @Column(name = "ends_at", nullable = false)
    val endsAt: Instant,
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "slot_hold_status")
    var status: SlotHoldStatus,
    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "job_status", columnDefinition = "job_status")
    var jobStatus: JobStatus? = null,
    @Column(name = "arrived_at")
    var arrivedAt: Instant? = null,
    @Column(name = "started_at")
    var startedAt: Instant? = null,
    @Column(name = "completed_at")
    var completedAt: Instant? = null,
    @Column(name = "arrival_latitude")
    var arrivalLatitude: BigDecimal? = null,
    @Column(name = "arrival_longitude")
    var arrivalLongitude: BigDecimal? = null,
    @Column(name = "arrival_accuracy_meters")
    var arrivalAccuracyMeters: BigDecimal? = null,
    @Version
    var version: Long = 0,
) {
    fun confirm() {
        check(status == SlotHoldStatus.HELD) { "Only held slots can be confirmed" }
        status = SlotHoldStatus.CONFIRMED
        jobStatus = JobStatus.ASSIGNED
    }

    fun expire() {
        if (status == SlotHoldStatus.HELD) status = SlotHoldStatus.EXPIRED
    }

    fun release() {
        if (status == SlotHoldStatus.HELD) status = SlotHoldStatus.RELEASED
    }

    fun cancelAssignment() {
        check(status == SlotHoldStatus.CONFIRMED && jobStatus == JobStatus.ASSIGNED) {
            "Only an assigned visit that has not started arrival can be cancelled"
        }
        status = SlotHoldStatus.RELEASED
        jobStatus = null
    }

    fun cancelByPlatform() {
        check(status == SlotHoldStatus.CONFIRMED &&
            (jobStatus == JobStatus.ASSIGNED || jobStatus == JobStatus.ARRIVED)
        ) { "Platform cannot cancel a visit after service has started" }
        status = SlotHoldStatus.RELEASED
        jobStatus = null
    }

    fun isActive(now: Instant): Boolean =
        status == SlotHoldStatus.CONFIRMED || (status == SlotHoldStatus.HELD && expiresAt.isAfter(now))

    fun arrive(latitude: BigDecimal, longitude: BigDecimal, accuracyMeters: BigDecimal, now: Instant) {
        if (jobStatus == JobStatus.ARRIVED) return
        check(status == SlotHoldStatus.CONFIRMED && jobStatus == JobStatus.ASSIGNED) {
            "Only an assigned confirmed visit can be marked arrived"
        }
        jobStatus = JobStatus.ARRIVED
        arrivedAt = now
        arrivalLatitude = latitude
        arrivalLongitude = longitude
        arrivalAccuracyMeters = accuracyMeters
    }

    fun start(now: Instant) {
        if (jobStatus == JobStatus.IN_PROGRESS) return
        check(jobStatus == JobStatus.ARRIVED) { "Worker must arrive before starting the visit" }
        jobStatus = JobStatus.IN_PROGRESS
        startedAt = now
    }

    fun complete(now: Instant) {
        if (jobStatus == JobStatus.COMPLETED) return
        check(jobStatus == JobStatus.IN_PROGRESS) { "Worker must start before completing the visit" }
        jobStatus = JobStatus.COMPLETED
        completedAt = now
    }
}
