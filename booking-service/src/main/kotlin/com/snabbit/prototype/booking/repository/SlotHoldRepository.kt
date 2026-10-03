package com.snabbit.prototype.booking.repository

import com.snabbit.prototype.booking.domain.enums.JobStatus
import com.snabbit.prototype.booking.domain.enums.SlotHoldStatus
import com.snabbit.prototype.booking.domain.SlotHold
import jakarta.persistence.LockModeType
import java.time.Instant
import java.util.UUID
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface SlotHoldRepository : JpaRepository<SlotHold, UUID> {
    fun findAllByBookingIdOrderByVisitIndex(bookingId: UUID): List<SlotHold>

    fun findByWorkerIdAndStatusAndJobStatusInAndStartsAtAfterOrderByStartsAtAsc(
        workerId: UUID,
        status: SlotHoldStatus,
        jobStatuses: Collection<JobStatus>,
        startsAt: Instant,
        pageable: Pageable,
    ): List<SlotHold>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select hold from SlotHold hold where hold.id = :id")
    fun findForUpdate(@Param("id") id: UUID): SlotHold?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select hold from SlotHold hold where hold.bookingId = :bookingId and hold.visitIndex = :visitIndex")
    fun findVisitForUpdate(
        @Param("bookingId") bookingId: UUID,
        @Param("visitIndex") visitIndex: Int,
    ): SlotHold?

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

    @Query(
        value = """
            SELECT id FROM slot_hold
            WHERE status = 'HELD' AND expires_at <= :now
            ORDER BY expires_at
            LIMIT :limit
        """,
        nativeQuery = true,
    )
    fun findExpiredIds(
        @Param("now") now: Instant,
        @Param("limit") limit: Int,
    ): List<UUID>
}
