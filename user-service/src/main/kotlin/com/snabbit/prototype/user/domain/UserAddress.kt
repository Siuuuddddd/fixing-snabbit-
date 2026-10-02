package com.snabbit.prototype.user.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "user_address")
class UserAddress(
    @Id val id: UUID,
    @Column(name = "user_id", nullable = false) val userId: UUID,
    @Column(nullable = false) val label: String,
    @Column(name = "address_line", nullable = false) val addressLine: String,
    @Column(nullable = false) val city: String,
    @Column(nullable = false) val pincode: String,
    @Column(nullable = false) val latitude: BigDecimal,
    @Column(nullable = false) val longitude: BigDecimal,
    @Column(nullable = false) val verified: Boolean,
    @Column(name = "created_at", nullable = false) val createdAt: Instant,
)
