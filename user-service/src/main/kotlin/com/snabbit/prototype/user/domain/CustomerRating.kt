package com.snabbit.prototype.user.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "customer_rating")
class CustomerRating(
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
    @Column(nullable = false)
    val stars: Int,
    val comment: String?,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
)
