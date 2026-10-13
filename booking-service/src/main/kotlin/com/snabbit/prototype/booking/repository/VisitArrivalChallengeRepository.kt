package com.snabbit.prototype.booking.repository

import com.snabbit.prototype.booking.domain.VisitArrivalChallenge
import jakarta.persistence.LockModeType
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface VisitArrivalChallengeRepository : JpaRepository<VisitArrivalChallenge, UUID> {
    fun findBySlotHoldId(slotHoldId: UUID): VisitArrivalChallenge?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select challenge from VisitArrivalChallenge challenge where challenge.slotHoldId = :slotHoldId")
    fun findBySlotHoldIdForUpdate(@Param("slotHoldId") slotHoldId: UUID): VisitArrivalChallenge?
}
