package com.snabbit.prototype.user.plus

import com.snabbit.prototype.user.dto.response.ExpiredPassReservation

import com.snabbit.prototype.user.domain.enums.PlusPassStatus

import com.snabbit.prototype.user.repository.PlusPassRepository
import com.snabbit.prototype.user.rating.UserIntegrationProperties
import com.snabbit.prototype.user.security.InternalServiceClientFactory
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.RestClientResponseException
import java.time.Clock
import java.time.Instant
import java.util.UUID

@Service
class PlusReconciliationQuery(private val passes: PlusPassRepository) {
    @Transactional(readOnly = true)
    fun expired(now: Instant): List<ExpiredPassReservation> =
        passes.findTop100ByStatusAndHoldExpiresAtBeforeOrderByHoldExpiresAt(PlusPassStatus.HELD, now)
            .mapNotNull { pass ->
                pass.heldForBookingId?.let { ExpiredPassReservation(pass.id, it, pass.userId) }
            }
}
