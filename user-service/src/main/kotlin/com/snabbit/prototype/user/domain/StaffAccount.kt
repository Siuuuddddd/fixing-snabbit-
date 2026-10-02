package com.snabbit.prototype.user.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

enum class StaffRole { SUPPORT_AGENT, OPERATIONS_MANAGER, ADMIN, FINANCE_OPERATOR }
enum class StaffStatus { ACTIVE, DISABLED }

@Entity
@Table(name = "staff_account")
class StaffAccount(
    @Id
    val id: UUID,
    @Column(name = "phone_e164", nullable = false, unique = true)
    val phoneE164: String,
    @Column(name = "display_name", nullable = false)
    val displayName: String,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val role: StaffRole,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val status: StaffStatus,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
)
