package com.snabbit.prototype.booking.service

import com.snabbit.prototype.booking.config.BookingProperties
import com.snabbit.prototype.booking.domain.enums.ArrivalOtpStatus
import com.snabbit.prototype.booking.domain.enums.JobStatus
import com.snabbit.prototype.booking.dto.response.ArrivalOtpStatusSnapshot
import com.snabbit.prototype.booking.exception.ArrivalOtpAccessDenied
import com.snabbit.prototype.booking.exception.ArrivalOtpConflict
import com.snabbit.prototype.booking.exception.BookingNotFound
import com.snabbit.prototype.booking.domain.enums.BookingStatus
import com.snabbit.prototype.booking.otp.ArrivalOtpCheckResult
import com.snabbit.prototype.booking.otp.ArrivalOtpProvider
import com.snabbit.prototype.booking.otp.ArrivalOtpProviderUnavailable
import com.snabbit.prototype.booking.repository.BookingRepository
import com.snabbit.prototype.booking.repository.SlotHoldRepository
import com.snabbit.prototype.booking.repository.VisitArrivalChallengeRepository
import java.time.Clock
import java.util.UUID
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ArrivalOtpCommandService(
    private val holds: SlotHoldRepository,
    private val challenges: VisitArrivalChallengeRepository,
    private val bookings: BookingRepository,
    private val provider: ArrivalOtpProvider,
    private val rateLimiter: ArrivalOtpVerificationRateLimiter,
    private val outbox: OutboxWriter,
    private val properties: BookingProperties,
    private val events: ApplicationEventPublisher,
    private val clock: Clock,
) {
    @Transactional(noRollbackFor = [ArrivalOtpConflict::class])
    fun verifyForCustomer(
        bookingId: UUID,
        visitIndex: Int,
        customerId: UUID,
        code: String,
    ): ArrivalOtpStatusSnapshot {
        val booking = bookings.findById(bookingId).orElseThrow { BookingNotFound(bookingId) }
        if (booking.customerId != customerId) throw ArrivalOtpAccessDenied()
        val hold = holds.findVisitForUpdate(bookingId, visitIndex) ?: throw BookingNotFound(bookingId)
        return verifyAndStart(bookingId, visitIndex, hold.workerId, code)
    }

    @Transactional(noRollbackFor = [ArrivalOtpConflict::class])
    fun verifyAndStart(
        bookingId: UUID,
        visitIndex: Int,
        workerId: UUID,
        code: String,
    ): ArrivalOtpStatusSnapshot {
        val now = clock.instant()
        val booking = bookings.findForUpdate(bookingId) ?: throw BookingNotFound(bookingId)
        if (booking.status != BookingStatus.CONFIRMED) {
            throw ArrivalOtpConflict("ARRIVAL_OTP_NOT_AVAILABLE", "Booking is not confirmed")
        }
        val hold = holds.findVisitForUpdate(bookingId, visitIndex) ?: throw BookingNotFound(bookingId)
        if (hold.workerId != workerId) throw ArrivalOtpAccessDenied()
        val challenge = challenges.findBySlotHoldIdForUpdate(hold.id)
            ?: throw ArrivalOtpConflict("ARRIVAL_OTP_NOT_AVAILABLE", "Arrival OTP challenge is not available")
        if (challenge.assignedWorkerId != workerId || challenge.bookingId != bookingId || challenge.visitIndex != visitIndex) {
            throw ArrivalOtpAccessDenied()
        }
        if (challenge.status == com.snabbit.prototype.booking.domain.enums.ArrivalChallengeStatus.VERIFIED &&
            hold.jobStatus == JobStatus.IN_PROGRESS
        ) {
            return ArrivalOtpStatusSnapshot(ArrivalOtpStatus.VERIFIED.name, ArrivalOtpStatus.VERIFIED.displayText)
        }
        if (hold.jobStatus != JobStatus.ARRIVED ||
            challenge.status != com.snabbit.prototype.booking.domain.enums.ArrivalChallengeStatus.SENT
        ) {
            throw ArrivalOtpConflict("ARRIVAL_OTP_NOT_AVAILABLE", "Arrival OTP cannot be verified in its current state")
        }
        if (!now.isBefore(challenge.expiresAt)) {
            challenge.expire(now)
            throw ArrivalOtpConflict("ARRIVAL_OTP_EXPIRED", "Arrival OTP has expired")
        }
        rateLimiter.check(
            challenge.id,
            workerId,
            properties.arrivalOtp.verificationRateWindow,
            properties.arrivalOtp.maxVerificationRequestsPerVisit,
            properties.arrivalOtp.maxVerificationRequestsPerWorker,
        )
        val result = try {
            provider.check(challenge.destinationPhoneE164, code)
        } catch (_: ArrivalOtpProviderUnavailable) {
            throw ArrivalOtpConflict("ARRIVAL_OTP_PROVIDER_UNAVAILABLE", "Arrival verification is temporarily unavailable")
        }
        when (result) {
            ArrivalOtpCheckResult.APPROVED -> {
                challenge.verify(now)
                hold.start(now)
                outbox.append(
                    bookingId,
                    "ServiceStarted",
                    mapOf("bookingId" to bookingId, "visitIndex" to visitIndex, "workerId" to workerId, "occurredAt" to now),
                    now,
                )
            }
            ArrivalOtpCheckResult.WRONG_CODE -> {
                val locked = challenge.recordWrongAttempt(now, properties.arrivalOtp.maxAttempts)
                if (locked) appendLocked(challenge.bookingId, challenge.visitIndex, workerId, now)
                throw ArrivalOtpConflict(
                    if (locked) "ARRIVAL_OTP_LOCKED" else "ARRIVAL_OTP_INVALID",
                    if (locked) "Arrival OTP is locked" else "Arrival OTP is invalid",
                )
            }
            ArrivalOtpCheckResult.EXPIRED -> {
                challenge.expire(now)
                throw ArrivalOtpConflict("ARRIVAL_OTP_EXPIRED", "Arrival OTP has expired")
            }
            ArrivalOtpCheckResult.LOCKED -> {
                challenge.lock(now)
                appendLocked(challenge.bookingId, challenge.visitIndex, workerId, now)
                throw ArrivalOtpConflict("ARRIVAL_OTP_LOCKED", "Arrival OTP is locked")
            }
        }
        return ArrivalOtpStatusSnapshot(ArrivalOtpStatus.VERIFIED.name, ArrivalOtpStatus.VERIFIED.displayText)
    }

    private fun appendLocked(bookingId: UUID, visitIndex: Int, workerId: UUID, now: java.time.Instant) {
        outbox.append(
            bookingId,
            "VisitOtpLocked",
            mapOf("bookingId" to bookingId, "visitIndex" to visitIndex, "workerId" to workerId, "otpStatus" to "LOCKED"),
            now,
        )
    }

    @Transactional
    fun resend(bookingId: UUID, visitIndex: Int, workerId: UUID): ArrivalOtpStatusSnapshot {
        val hold = holds.findVisitForUpdate(bookingId, visitIndex) ?: throw BookingNotFound(bookingId)
        if (hold.workerId != workerId) throw ArrivalOtpAccessDenied()
        if (hold.jobStatus != JobStatus.ARRIVED) {
            throw ArrivalOtpConflict("ARRIVAL_OTP_NOT_AVAILABLE", "Arrival OTP is available only after a validated arrival")
        }
        val challenge = challenges.findBySlotHoldIdForUpdate(hold.id)
            ?: throw ArrivalOtpConflict("ARRIVAL_OTP_NOT_AVAILABLE", "Arrival OTP challenge is not available")
        val now = clock.instant()
        if (challenge.sendCount >= properties.arrivalOtp.maxSends) {
            throw ArrivalOtpConflict("OTP_LIMIT_REACHED", "Arrival OTP send limit reached")
        }
        val allowedAt = challenge.lastSentAt?.plus(properties.arrivalOtp.resendCooldown)
        if (allowedAt != null && now.isBefore(allowedAt)) {
            throw ArrivalOtpConflict(
                "ARRIVAL_OTP_RESEND_COOLDOWN",
                "Please wait before requesting another arrival OTP",
                java.time.Duration.between(now, allowedAt).seconds.coerceAtLeast(1),
            )
        }
        try {
            challenge.requestResend(now, properties.arrivalOtp.resendCooldown, properties.arrivalOtp.maxSends)
        } catch (_: IllegalStateException) {
            throw ArrivalOtpConflict("ARRIVAL_OTP_NOT_AVAILABLE", "Arrival OTP cannot be resent in its current state")
        }
        events.publishEvent(ArrivalChallengeCreatedEvent(challenge.id))
        return ArrivalOtpStatusSnapshot(
            ArrivalOtpStatus.OTP_SENT.name,
            "Arrival OTP resend requested",
            properties.arrivalOtp.maxSends - challenge.sendCount,
            properties.arrivalOtp.resendCooldown.seconds,
        )
    }

    @Transactional
    fun resendForCustomer(bookingId: UUID, visitIndex: Int, customerId: UUID): ArrivalOtpStatusSnapshot {
        val booking = bookings.findById(bookingId).orElseThrow { BookingNotFound(bookingId) }
        if (booking.customerId != customerId) throw ArrivalOtpAccessDenied()
        val hold = holds.findVisitForUpdate(bookingId, visitIndex) ?: throw BookingNotFound(bookingId)
        return resend(bookingId, visitIndex, hold.workerId)
    }
}
