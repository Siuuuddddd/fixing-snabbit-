package com.snabbit.prototype.worker.domain

import com.snabbit.prototype.worker.domain.enums.AvailabilityStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Enumerated
import jakarta.persistence.EnumType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

@Entity
@Table(name = "worker_availability")
class WorkerAvailability(
    @Id
    val id: UUID,
    @Column(name = "worker_id", nullable = false)
    val workerId: UUID,
    @Column(name = "service_date", nullable = false)
    val serviceDate: LocalDate,
    @Column(name = "available_from", nullable = false)
    var availableFrom: LocalTime,
    @Column(name = "available_until", nullable = false)
    var availableUntil: LocalTime,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: AvailabilityStatus,
    @Version
    var version: Long = 0,
)
