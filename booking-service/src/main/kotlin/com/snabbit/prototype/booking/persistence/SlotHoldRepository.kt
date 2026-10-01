package com.snabbit.prototype.booking.persistence

import com.snabbit.prototype.booking.domain.SlotHold
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface SlotHoldRepository : JpaRepository<SlotHold, UUID> {
    @Query(
        value = """
            SELECT EXISTS (
                SELECT 1 FROM slot_hold
                WHERE worker_id = :workerId
                  AND status IN ('HELD', 'CONFIRMED')
                  AND (status = 'CONFIRMED' OR expires_at > :now)
                  AND starts_at < :endsAt
                  AND ends_at > :startsAt
            )
        """,
        nativeQuery = true,
    )
    fun hasActiveOverlap(
        @Param("workerId") workerId: UUID,
        @Param("startsAt") startsAt: Instant,
        @Param("endsAt") endsAt: Instant,
        @Param("now") now: Instant,
    ): Boolean
}
