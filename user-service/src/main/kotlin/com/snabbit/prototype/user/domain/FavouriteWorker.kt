package com.snabbit.prototype.user.domain

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.io.Serializable
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "favourite_worker")
class FavouriteWorker(
    @EmbeddedId
    val id: FavouriteWorkerId,
    @Column(nullable = false)
    val priority: Int,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
)
