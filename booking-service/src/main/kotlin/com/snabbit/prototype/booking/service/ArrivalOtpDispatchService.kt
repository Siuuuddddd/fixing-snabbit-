package com.snabbit.prototype.booking.service

import com.snabbit.prototype.booking.config.BookingProperties
import com.snabbit.prototype.booking.domain.enums.ArrivalChallengeStatus
import com.snabbit.prototype.booking.otp.ArrivalOtpProvider
import com.snabbit.prototype.booking.repository.VisitArrivalChallengeRepository
import com.snabbit.prototype.platform.IndianPhoneNumber
import java.time.Clock
import java.util.UUID
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ArrivalOtpDispatchService(
    private val challenges: VisitArrivalChallengeRepository,
    private val workers: WorkerContactClient,
    private val provider: ArrivalOtpProvider,
    private val outbox: OutboxWriter,
    private val properties: BookingProperties,
    private val clock: Clock,
) {
    @Transactional
    fun dispatch(challengeId: UUID) {
        val challenge = challenges.findByIdForUpdate(challengeId) ?: return
        if (challenge.status !in setOf(ArrivalChallengeStatus.PENDING, ArrivalChallengeStatus.DELIVERY_FAILED)) return
        if (challenge.sendCount >= properties.arrivalOtp.maxSends) return
        val now = clock.instant()
        challenge.beginSend(now, properties.arrivalOtp.maxSends)

        val workerPhone = try {
            IndianPhoneNumber.normalize(workers.contact(challenge.assignedWorkerId).phoneNumber)
        } catch (_: RuntimeException) {
            challenge.deliveryFailed(now)
            if (challenge.sendCount >= properties.arrivalOtp.maxSends) {
                terminalFailure(challenge.bookingId, challenge.visitIndex, challenge.assignedWorkerId, now)
            }
            return
        }
        if (workerPhone == challenge.destinationPhoneE164) {
            challenge.blockDelivery(now, properties.arrivalOtp.maxSends)
            terminalFailure(challenge.bookingId, challenge.visitIndex, challenge.assignedWorkerId, now)
            return
        }

        try {
            val started = provider.start(challenge.destinationPhoneE164)
            challenge.sent(started.providerReference, now, now.plus(properties.arrivalOtp.expiry))
            outbox.append(
                challenge.bookingId,
                "ArrivalOtpSent",
                mapOf(
                    "bookingId" to challenge.bookingId,
                    "visitIndex" to challenge.visitIndex,
                    "workerId" to challenge.assignedWorkerId,
                    "otpStatus" to "OTP_SENT",
                ),
                now,
            )
        } catch (_: RuntimeException) {
            challenge.deliveryFailed(now)
            if (challenge.sendCount >= properties.arrivalOtp.maxSends) {
                terminalFailure(challenge.bookingId, challenge.visitIndex, challenge.assignedWorkerId, now)
            }
        }
    }

    private fun terminalFailure(bookingId: UUID, visitIndex: Int, workerId: UUID, now: java.time.Instant) {
        outbox.append(
            bookingId,
            "ArrivalOtpDeliveryFailed",
            mapOf(
                "bookingId" to bookingId,
                "visitIndex" to visitIndex,
                "workerId" to workerId,
                "otpStatus" to "DELIVERY_FAILED",
            ),
            now,
        )
    }
}
