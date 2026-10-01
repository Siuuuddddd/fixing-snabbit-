package com.snabbit.prototype.wallet.persistence

import com.snabbit.prototype.wallet.domain.TopUpOrder
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface TopUpOrderRepository : JpaRepository<TopUpOrder, UUID> {
    fun findByIdempotencyKey(idempotencyKey: String): TopUpOrder?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select topUp from TopUpOrder topUp where topUp.id = :id")
    fun findForUpdate(@Param("id") id: UUID): TopUpOrder?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select topUp from TopUpOrder topUp where topUp.providerOrderId = :providerOrderId")
    fun findByProviderOrderIdForUpdate(@Param("providerOrderId") providerOrderId: String): TopUpOrder?
}
