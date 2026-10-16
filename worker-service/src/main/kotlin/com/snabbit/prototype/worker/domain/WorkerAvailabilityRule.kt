package com.snabbit.prototype.worker.domain

import com.snabbit.prototype.worker.domain.enums.AvailabilityPeriod
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import java.time.LocalTime
import java.util.UUID

@Entity
@IdClass(WorkerAvailabilityRuleId::class)
@Table(name = "worker_availability_rule")
class WorkerAvailabilityRule(
    @Id
    @Column(name = "worker_id")
    val workerId: UUID,
    @Id
    @Enumerated(EnumType.STRING)
    val period: AvailabilityPeriod,
    @Column(name = "first_start", nullable = false)
    val firstStart: LocalTime,
    @Column(name = "last_start", nullable = false)
    val lastStart: LocalTime,
) {
    fun containsStart(start: LocalTime): Boolean = start >= firstStart && start <= lastStart
}
