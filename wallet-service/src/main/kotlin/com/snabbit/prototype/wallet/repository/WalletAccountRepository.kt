package com.snabbit.prototype.wallet.repository

import com.snabbit.prototype.wallet.domain.WalletAccount
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface WalletAccountRepository : JpaRepository<WalletAccount, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select wallet from WalletAccount wallet where wallet.id = :id")
    fun findForUpdate(@Param("id") id: UUID): WalletAccount?
}
