package com.snabbit.prototype.worker.domain

import jakarta.persistence.Column
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "worker_skill")
class WorkerSkill(
    @EmbeddedId
    val id: WorkerSkillId,
    @Column(name = "base_price_per_hour_minor", nullable = false)
    val basePricePerHourMinor: Long,
    @Column(nullable = false)
    val enabled: Boolean,
)
