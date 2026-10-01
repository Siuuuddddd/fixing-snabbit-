package com.snabbit.prototype.wallet.persistence

import com.snabbit.prototype.wallet.domain.WalletOutboxEvent
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface WalletOutboxRepository : JpaRepository<WalletOutboxEvent, UUID>
