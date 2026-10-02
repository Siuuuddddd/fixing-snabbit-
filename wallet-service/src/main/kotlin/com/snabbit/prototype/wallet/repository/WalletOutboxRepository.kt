package com.snabbit.prototype.wallet.repository

import com.snabbit.prototype.wallet.domain.WalletOutboxEvent
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface WalletOutboxRepository : JpaRepository<WalletOutboxEvent, UUID>
