package com.snabbit.prototype.worker.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "worker_location_history")
class WorkerLocationPoint(
    @Id
    val id: UUID,
    @Column(name = "worker_id", nullable = false)
    val workerId: UUID,
    @Column(nullable = false)
    val latitude: BigDecimal,
    @Column(nullable = false)
    val longitude: BigDecimal,
    @Column(name = "accuracy_meters", nullable = false)
    val accuracyMeters: BigDecimal,
    @Column(name = "captured_at", nullable = false)
    val capturedAt: Instant,
    @Column(name = "received_at", nullable = false)
    val receivedAt: Instant,
)
