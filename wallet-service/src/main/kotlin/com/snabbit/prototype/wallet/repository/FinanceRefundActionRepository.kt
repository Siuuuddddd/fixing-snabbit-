package com.snabbit.prototype.wallet.repository

import com.snabbit.prototype.wallet.domain.FinanceRefundAction
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

interface FinanceRefundActionRepository : JpaRepository<FinanceRefundAction, UUID>
