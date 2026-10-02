package com.snabbit.prototype.user.plus

import com.snabbit.prototype.user.application.UserNotFound
import com.snabbit.prototype.user.domain.PlusPass
import com.snabbit.prototype.user.domain.PlusPassStatus
import com.snabbit.prototype.user.domain.PlusSubscription
import com.snabbit.prototype.user.persistence.PlusPassRepository
import com.snabbit.prototype.user.persistence.PlusSubscriptionRepository
import com.snabbit.prototype.user.persistence.UserProfileRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.util.UUID
import kotlin.math.min

data class PlusStatusSnapshot(
    val userId: UUID,
    val active: Boolean,
    val expiresAt: Instant?,
    val availablePasses: Long,
)

data class ActivatePlusCommand(
    val sourceReference: String,
    val startsAt: Instant,
    val expiresAt: Instant,
    val passCount: Int,
    val passDiscountMinor: Long,
    val serviceCode: String?,
)

data class PassReservationSnapshot(
    val passId: UUID?,
    val bookingId: UUID,
    val grossTotalMinor: Long,
    val discountMinor: Long,
    val netTotalMinor: Long,
    val expiresAt: Instant?,
)

class PlusRequestRejected(message: String) : RuntimeException(message)

@Service
class PlusService(
    private val users: UserProfileRepository,
    private val subscriptions: PlusSubscriptionRepository,
    private val passes: PlusPassRepository,
    private val clock: Clock,
) {
    @Transactional
    fun status(userId: UUID): PlusStatusSnapshot {
        val now = clock.instant()
        val user = users.findById(userId).orElseThrow { UserNotFound(userId) }
        return PlusStatusSnapshot(
            user.id,
            user.hasActivePlus(now),
            user.plusExpiresAt,
            passes.countByUserIdAndStatusAndExpiresAtAfter(userId, PlusPassStatus.AVAILABLE, now),
        )
    }

    @Transactional
    fun activate(userId: UUID, command: ActivatePlusCommand): PlusStatusSnapshot {
        validateActivation(command)
        val sourceReference = command.sourceReference.trim()
        subscriptions.findBySourceReference(sourceReference)?.let { existing ->
            if (existing.userId != userId) throw PlusRequestRejected("Subscription reference belongs to another customer")
            return status(userId)
        }
        val user = users.findForUpdate(userId) ?: throw UserNotFound(userId)
        val now = clock.instant()
        val subscription = subscriptions.save(
            PlusSubscription(
                UUID.randomUUID(), userId, sourceReference, command.startsAt, command.expiresAt, now,
            ),
        )
        if (command.passCount > 0) {
            passes.saveAll(
                (1..command.passCount).map {
                    PlusPass(
                        UUID.randomUUID(), subscription.id, userId,
                        command.serviceCode?.trim()?.uppercase()?.takeIf(String::isNotBlank),
                        command.passDiscountMinor, PlusPassStatus.AVAILABLE, null, null, null,
                        command.expiresAt, now,
                    )
                },
            )
        }
        user.plusActive = true
        user.plusExpiresAt = maxOf(user.plusExpiresAt ?: command.expiresAt, command.expiresAt)
        user.updatedAt = now
        return status(userId)
    }

    @Transactional
    fun reservePass(
        userId: UUID,
        bookingId: UUID,
        serviceCode: String,
        grossTotalMinor: Long,
        holdUntil: Instant,
    ): PassReservationSnapshot {
        if (grossTotalMinor <= 0) throw PlusRequestRejected("Gross total must be positive")
        val now = clock.instant()
        passes.findByBookingForUpdate(bookingId)?.let {
            if (it.userId != userId) throw PlusRequestRejected("Pass reservation belongs to another customer")
            return reservation(it, bookingId, grossTotalMinor)
        }
        val user = users.findForUpdate(userId) ?: throw UserNotFound(userId)
        if (!user.hasActivePlus(now)) return noDiscount(bookingId, grossTotalMinor)
        val pass = passes.findEligibleForUpdate(
            userId, serviceCode.trim().uppercase(), PlusPassStatus.AVAILABLE, holdUntil,
        ).firstOrNull() ?: return noDiscount(bookingId, grossTotalMinor)
        pass.reserve(bookingId, holdUntil, now)
        return reservation(pass, bookingId, grossTotalMinor)
    }

    @Transactional
    fun consumePass(bookingId: UUID, confirmedAt: Instant) {
        val pass = passes.findByBookingForUpdate(bookingId) ?: return
        if (pass.status == PlusPassStatus.USED) return
        try {
            pass.consume(confirmedAt)
        } catch (error: IllegalArgumentException) {
            throw PlusRequestRejected(error.message.orEmpty())
        } catch (error: IllegalStateException) {
            throw PlusRequestRejected(error.message.orEmpty())
        }
    }

    @Transactional
    fun releasePass(bookingId: UUID) {
        passes.findByBookingForUpdate(bookingId)?.release()
    }

    @Transactional
    fun reconcileConfirmed(bookingId: UUID, passId: UUID, confirmedAt: Instant) {
        val pass = passes.findByBookingForUpdate(bookingId) ?: return
        if (pass.id != passId) throw PlusRequestRejected("Booking references a different Plus pass")
        if (pass.status == PlusPassStatus.USED) return
        pass.reconcileConfirmed(confirmedAt)
    }

    private fun reservation(pass: PlusPass, bookingId: UUID, gross: Long): PassReservationSnapshot {
        val discount = min(pass.discountMinor, gross - 1)
        return PassReservationSnapshot(pass.id, bookingId, gross, discount, gross - discount, pass.holdExpiresAt)
    }

    private fun noDiscount(bookingId: UUID, gross: Long) =
        PassReservationSnapshot(null, bookingId, gross, 0, gross, null)

    private fun validateActivation(command: ActivatePlusCommand) {
        if (command.sourceReference.isBlank() || command.sourceReference.length > 120) {
            throw PlusRequestRejected("A valid payment source reference is required")
        }
        val now = clock.instant()
        if (command.startsAt.isAfter(now)) throw PlusRequestRejected("Subscription cannot start in the future")
        if (!command.expiresAt.isAfter(now) || !command.expiresAt.isAfter(command.startsAt)) {
            throw PlusRequestRejected("Subscription expiry must be in the future and follow its start")
        }
        if (command.passCount !in 0..20) throw PlusRequestRejected("Pass count must be between 0 and 20")
        if (command.passCount > 0 && command.passDiscountMinor <= 0) {
            throw PlusRequestRejected("Pass discount must be positive when passes are granted")
        }
    }
}
