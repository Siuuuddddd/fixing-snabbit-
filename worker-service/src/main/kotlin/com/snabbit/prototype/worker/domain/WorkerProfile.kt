package com.snabbit.prototype.worker.domain

import com.snabbit.prototype.worker.domain.enums.WorkerStatus
import com.snabbit.prototype.worker.domain.enums.WorkerTier
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Enumerated
import jakarta.persistence.EnumType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "worker_profile")
class WorkerProfile(
    @Id
    val id: UUID,
    @Column(name = "phone_e164", nullable = false, unique = true)
    val phoneE164: String,
    @Column(name = "display_name", nullable = false)
    var displayName: String,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var tier: WorkerTier,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: WorkerStatus,
    @Column(name = "current_latitude", nullable = false)
    var currentLatitude: BigDecimal,
    @Column(name = "current_longitude", nullable = false)
    var currentLongitude: BigDecimal,
    @Column(name = "service_radius_km", nullable = false)
    var serviceRadiusKm: BigDecimal,
    @Column(name = "rating_sum", nullable = false)
    var ratingSum: Int,
    @Column(name = "rating_count", nullable = false)
    var ratingCount: Int,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant,
    @Column(name = "joined_at", nullable = false)
    var joinedAt: Instant = createdAt,
    @Version
    var version: Long = 0,
) {
    fun averageRating(): BigDecimal = if (ratingCount == 0) {
        BigDecimal.ZERO
    } else {
        BigDecimal(ratingSum).divide(BigDecimal(ratingCount), 2, RoundingMode.HALF_UP)
    }
}
