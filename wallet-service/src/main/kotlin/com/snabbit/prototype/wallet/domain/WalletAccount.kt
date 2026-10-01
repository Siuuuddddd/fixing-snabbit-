package com.snabbit.prototype.wallet.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "wallet_account")
class WalletAccount(
    @Id
    val id: UUID,
    @Column(name = "customer_id", nullable = false, unique = true)
    val customerId: UUID,
    @Column(nullable = false, length = 3)
    val currency: String,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
    @Version
    var version: Long = 0,
)
