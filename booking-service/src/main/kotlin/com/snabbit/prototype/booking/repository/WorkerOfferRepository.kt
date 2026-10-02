package com.snabbit.prototype.booking.repository

import com.snabbit.prototype.booking.domain.WorkerOffer
import com.snabbit.prototype.booking.domain.WorkerOfferStatus
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.data.domain.Pageable
import java.time.Instant
import java.util.UUID

interface WorkerOfferRepository : JpaRepository<WorkerOffer, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select offer from WorkerOffer offer where offer.id = :offerId and offer.bookingId = :bookingId")
    fun findForUpdate(
        @Param("bookingId") bookingId: UUID,
        @Param("offerId") offerId: UUID,
    ): WorkerOffer?

    fun findAllByBookingIdOrderByCreatedAt(bookingId: UUID): List<WorkerOffer>

    fun countByBookingIdAndStatusNot(bookingId: UUID, status: WorkerOfferStatus): Long

    fun findByWorkerIdAndStatusAndExpiresAtAfterOrderByExpiresAtAsc(
        workerId: UUID,
        status: WorkerOfferStatus,
        expiresAt: Instant,
        pageable: Pageable,
    ): List<WorkerOffer>

    @Query(
        value = """
            SELECT id FROM worker_offer
            WHERE status = 'OFFERED' AND expires_at <= :now
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
