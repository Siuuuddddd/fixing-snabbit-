package com.snabbit.prototype.user.plus

import com.snabbit.prototype.user.domain.enums.PlusPassStatus
import com.snabbit.prototype.user.dto.response.ExpiredPassReservation
import com.snabbit.prototype.user.repository.PlusPassRepository
import java.time.Instant
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PlusReconciliationQuery(private val passes: PlusPassRepository) {
    @Transactional(readOnly = true)
    fun expired(now: Instant): List<ExpiredPassReservation> =
        passes.findTop100ByStatusAndHoldExpiresAtBeforeOrderByHoldExpiresAt(PlusPassStatus.HELD, now)
            .mapNotNull { pass ->
                pass.heldForBookingId?.let { ExpiredPassReservation(pass.id, it, pass.userId) }
            }
}
