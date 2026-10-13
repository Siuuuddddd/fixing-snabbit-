package com.snabbit.prototype.booking.service

import com.snabbit.prototype.booking.config.BookingProperties
import com.snabbit.prototype.booking.domain.enums.ArrivalChallengeStatus
import com.snabbit.prototype.booking.repository.VisitArrivalChallengeRepository
import java.time.Clock
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class ArrivalOtpRetryScheduler(
    private val challenges: VisitArrivalChallengeRepository,
    private val dispatcher: ArrivalOtpDispatchService,
    private val properties: BookingProperties,
    private val clock: Clock,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${booking.arrival-otp.retry-scan-delay:PT5S}")
    fun retryPending() {
        val now = clock.instant()
        val retryable = runCatching {
            challenges.findTop100ByStatusInOrderByUpdatedAtAsc(
                setOf(ArrivalChallengeStatus.PENDING, ArrivalChallengeStatus.DELIVERY_FAILED),
            )
        }.onFailure {
            logger.debug("Arrival OTP retry scan skipped because persistence is unavailable")
        }.getOrElse { return }
        retryable.filter { challenge ->
            challenge.sendCount < properties.arrivalOtp.maxSends &&
                (challenge.status == ArrivalChallengeStatus.PENDING ||
                    !challenge.updatedAt.plus(properties.arrivalOtp.resendCooldown).isAfter(now))
        }.forEach { dispatcher.dispatch(it.id) }
    }
}
