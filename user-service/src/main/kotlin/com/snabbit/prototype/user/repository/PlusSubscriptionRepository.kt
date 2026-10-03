package com.snabbit.prototype.user.repository

import com.snabbit.prototype.user.domain.PlusSubscription
import java.time.Instant
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

interface PlusSubscriptionRepository : JpaRepository<PlusSubscription, UUID> {
    fun findBySourceReference(sourceReference: String): PlusSubscription?
    fun findFirstByUserIdAndExpiresAtAfterOrderByExpiresAtDesc(userId: UUID, now: Instant): PlusSubscription?
}
