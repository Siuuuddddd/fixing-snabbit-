package com.snabbit.prototype.booking.service

import com.snabbit.prototype.booking.config.BookingProperties
import com.snabbit.prototype.booking.domain.Booking
import com.snabbit.prototype.booking.domain.SlotHold
import com.snabbit.prototype.booking.domain.VisitArrivalChallenge
import com.snabbit.prototype.booking.domain.enums.ArrivalChallengeStatus
import com.snabbit.prototype.booking.exception.ArrivalOtpRequired
import com.snabbit.prototype.booking.exception.InvalidBookingState
import com.snabbit.prototype.booking.domain.enums.BookingStatus
import com.snabbit.prototype.booking.domain.enums.JobStatus
import com.snabbit.prototype.booking.repository.VisitArrivalChallengeRepository
import java.time.Instant
import java.util.UUID
import org.springframework.stereotype.Service

@Service
class ArrivalChallengeService(
    private val challenges: VisitArrivalChallengeRepository,
    private val homeContacts: HomeContactService,
    private val properties: BookingProperties,
) {
    fun createForValidatedArrival(booking: Booking, hold: SlotHold, now: Instant): VisitArrivalChallenge {
        challenges.findBySlotHoldId(hold.id)?.let { return it }
        if (booking.status != BookingStatus.CONFIRMED || hold.jobStatus != JobStatus.ARRIVED) {
            throw InvalidBookingState("Arrival challenge requires a validated worker arrival")
        }
        homeContacts.ensureResolvedForArrival(booking, now)
        val challenge = VisitArrivalChallenge(
            id = UUID.randomUUID(),
            bookingId = booking.id,
            visitIndex = hold.visitIndex,
            slotHoldId = hold.id,
            assignedWorkerId = hold.workerId,
            destinationPhoneE164 = requireNotNull(booking.homeContactPhoneE164),
            status = ArrivalChallengeStatus.PENDING,
            expiresAt = now.plus(properties.arrivalOtp.expiry),
            createdAt = now,
            updatedAt = now,
        )
        return challenges.save(challenge)
    }

    fun requireVerified(bookingId: UUID, visitIndex: Int, hold: SlotHold) {
        if (!properties.arrivalOtp.required) return
        val challenge = challenges.findBySlotHoldIdForUpdate(hold.id)
        if (challenge?.status != ArrivalChallengeStatus.VERIFIED ||
            challenge.bookingId != bookingId || challenge.visitIndex != visitIndex ||
            challenge.assignedWorkerId != hold.workerId
        ) {
            throw ArrivalOtpRequired(bookingId, visitIndex)
        }
    }

    fun cancelForVisit(hold: SlotHold, now: Instant) {
        challenges.findBySlotHoldIdForUpdate(hold.id)?.cancel(now)
    }
}
