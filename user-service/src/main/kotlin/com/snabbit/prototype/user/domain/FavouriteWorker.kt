package com.snabbit.prototype.user.domain

import jakarta.persistence.Column
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "favourite_worker")
class FavouriteWorker(
    @EmbeddedId
    val id: FavouriteWorkerId,
    @Column(nullable = false)
    val priority: Int,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
    @Column(name = "eligibility_exempt", nullable = false)
    val eligibilityExempt: Boolean = false,
)
