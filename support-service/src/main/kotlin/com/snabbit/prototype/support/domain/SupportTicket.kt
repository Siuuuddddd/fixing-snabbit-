package com.snabbit.prototype.support.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant
import java.util.UUID

enum class SupportTicketStatus { OPEN, RESOLVED }

@Entity
@Table(name = "support_ticket")
class SupportTicket(
    @Id
    val id: UUID,
    @Column(name = "booking_id", nullable = false, unique = true)
    val bookingId: UUID,
    @Column(name = "customer_id", nullable = false)
    val customerId: UUID,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: SupportTicketStatus,
    @Column(nullable = false)
    val subject: String,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant,
    @Version
    var version: Long = 0,
) {
    fun resolve(now: Instant) {
        status = SupportTicketStatus.RESOLVED
        updatedAt = now
    }

    fun touch(now: Instant) {
        check(status == SupportTicketStatus.OPEN) { "Support ticket is resolved" }
        updatedAt = now
    }
}
