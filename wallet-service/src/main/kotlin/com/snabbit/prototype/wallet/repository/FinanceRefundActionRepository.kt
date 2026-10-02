package com.snabbit.prototype.wallet.repository

import com.snabbit.prototype.wallet.domain.FinanceRefundAction
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface FinanceRefundActionRepository : JpaRepository<FinanceRefundAction, UUID>
