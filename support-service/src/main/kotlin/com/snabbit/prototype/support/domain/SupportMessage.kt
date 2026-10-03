package com.snabbit.prototype.support.domain

import com.snabbit.prototype.support.domain.enums.SupportSenderType

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "support_message")
class SupportMessage(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "ticket_id", nullable = false)
    val ticketId: UUID,
    @Enumerated(EnumType.STRING)
    @Column(name = "sender_type", nullable = false)
    val senderType: SupportSenderType,
    @Column(name = "sender_id")
    val senderId: UUID?,
    @Column(nullable = false)
    val body: String,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
)
