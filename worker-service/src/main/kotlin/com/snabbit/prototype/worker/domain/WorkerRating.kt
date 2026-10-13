package com.snabbit.prototype.worker.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "worker_rating")
class WorkerRating(
    @Id
    val id: UUID,
    @Column(name = "booking_id", nullable = false)
    val bookingId: UUID,
    @Column(name = "visit_index", nullable = false)
    val visitIndex: Int,
    @Column(name = "worker_id", nullable = false)
    val workerId: UUID,
    @Column(name = "customer_id", nullable = false)
    val customerId: UUID,
    @Column(nullable = false)
    var stars: Int,
    var comment: String?,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = createdAt,
)
