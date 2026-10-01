package com.snabbit.prototype.booking.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

@Entity
@Table(name = "worker_day_capacity")
class WorkerDayCapacity(
    @Id
    val id: UUID,
    @Column(name = "worker_id", nullable = false)
    val workerId: UUID,
    @Column(name = "service_date", nullable = false)
    val serviceDate: LocalDate,
    @Column(name = "available_from", nullable = false)
    val availableFrom: LocalTime,
    @Column(name = "available_until", nullable = false)
    val availableUntil: LocalTime,
    @Version
    var version: Long = 0,
)
