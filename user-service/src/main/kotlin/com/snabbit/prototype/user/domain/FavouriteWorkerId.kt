package com.snabbit.prototype.user.domain

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.io.Serializable
import java.time.Instant
import java.util.UUID

@Embeddable
data class FavouriteWorkerId(
    @Column(name = "user_id")
    val userId: UUID = UUID(0, 0),
    @Column(name = "worker_id")
    val workerId: UUID = UUID(0, 0),
) : Serializable
