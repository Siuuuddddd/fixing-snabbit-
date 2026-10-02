package com.snabbit.prototype.wallet.persistence

import com.snabbit.prototype.wallet.domain.PlusPurchase
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface PlusPurchaseRepository : JpaRepository<PlusPurchase, UUID> {
    fun findByIdempotencyKey(idempotencyKey: String): PlusPurchase?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select purchase from PlusPurchase purchase where purchase.id = :id")
    fun findForUpdate(@Param("id") id: UUID): PlusPurchase?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select purchase from PlusPurchase purchase where purchase.providerOrderId = :providerOrderId")
    fun findByProviderOrderIdForUpdate(@Param("providerOrderId") providerOrderId: String): PlusPurchase?
}
