package com.snabbit.prototype.wallet.repository

import com.snabbit.prototype.wallet.domain.WalletOutboxEvent
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

interface WalletOutboxRepository : JpaRepository<WalletOutboxEvent, UUID>
