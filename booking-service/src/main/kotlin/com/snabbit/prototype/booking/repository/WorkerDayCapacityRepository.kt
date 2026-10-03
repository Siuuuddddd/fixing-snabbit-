package com.snabbit.prototype.booking.repository

import com.snabbit.prototype.booking.domain.WorkerDayCapacity
import jakarta.persistence.LockModeType
import java.time.LocalDate
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface WorkerDayCapacityRepository : JpaRepository<WorkerDayCapacity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        """
        select capacity from WorkerDayCapacity capacity
        where capacity.workerId = :workerId and capacity.serviceDate = :serviceDate
        """,
    )
    fun findForUpdate(
        @Param("workerId") workerId: UUID,
        @Param("serviceDate") serviceDate: LocalDate,
    ): WorkerDayCapacity?
}
