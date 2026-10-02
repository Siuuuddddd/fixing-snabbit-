package com.snabbit.prototype.booking.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "service_offering")
class ServiceOffering(
    @Id
    val id: UUID,
    @Column(nullable = false, unique = true)
    val code: String,
    @Column(name = "display_name", nullable = false)
    val displayName: String,
    @Column(name = "base_price_per_hour_minor", nullable = false)
    val basePricePerHourMinor: Long,
    @Column(nullable = false)
    val active: Boolean,
)
