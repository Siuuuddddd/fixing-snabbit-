package com.snabbit.prototype.user.plus

import com.snabbit.prototype.user.domain.enums.PlusPassStatus
import com.snabbit.prototype.user.domain.PlusPass
import com.snabbit.prototype.user.domain.PlusSubscription
import com.snabbit.prototype.user.dto.request.ActivatePlusCommand
import com.snabbit.prototype.user.dto.response.PassReservationSnapshot
import com.snabbit.prototype.user.dto.response.PlusStatusSnapshot
import com.snabbit.prototype.user.events.UserOutboxWriter
import com.snabbit.prototype.user.exception.PlusRequestRejected
import com.snabbit.prototype.user.exception.UserNotFound
import com.snabbit.prototype.user.repository.PlusPassRepository
import com.snabbit.prototype.user.repository.PlusSubscriptionRepository
import com.snabbit.prototype.user.repository.UserProfileRepository
import java.time.Clock
import java.time.Instant
import java.util.UUID
import kotlin.math.min
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PlusService(
    private val users: UserProfileRepository,
    private val subscriptions: PlusSubscriptionRepository,
    private val passes: PlusPassRepository,
    private val clock: Clock,
    private val outbox: UserOutboxWriter,
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
        val user = users.findForUpdate(userId) ?: throw UserNotFound(userId)
        subscriptions.findBySourceReference(sourceReference)?.let { existing ->
            if (existing.userId != userId) throw PlusRequestRejected("Subscription reference belongs to another customer")
            return status(userId)
        }
        val now = clock.instant()
        persistActivation(user, sourceReference, command, now)
        return status(userId)
    }

    @Transactional
    fun activatePurchase(
        userId: UUID,
        purchaseId: UUID,
        purchasedAt: Instant,
        durationDays: Int,
        passCount: Int,
        passDiscountMinor: Long,
        serviceCode: String?,
    ): PlusStatusSnapshot {
        if (durationDays !in 1..365) throw PlusRequestRejected("Subscription duration must be between 1 and 365 days")
        if (passCount !in 0..20) throw PlusRequestRejected("Pass count must be between 0 and 20")
        if (passDiscountMinor < 0 || passDiscountMinor % 100 != 0L ||
            (passCount > 0 && passDiscountMinor < 100)
        ) {
            throw PlusRequestRejected("Pass discount must use non-negative whole-rupee increments")
        }
        val sourceReference = "plus-purchase:$purchaseId"
        val user = users.findForUpdate(userId) ?: throw UserNotFound(userId)
        subscriptions.findBySourceReference(sourceReference)?.let { existing ->
            if (existing.userId != userId) throw PlusRequestRejected("Subscription reference belongs to another customer")
            return status(userId)
        }
        val startsAt = maxOf(purchasedAt, user.plusExpiresAt ?: purchasedAt)
        val command = ActivatePlusCommand(
            sourceReference,
            startsAt,
            startsAt.plusSeconds(durationDays.toLong() * 86_400),
            passCount,
            passDiscountMinor,
            serviceCode,
        )
        persistActivation(user, sourceReference, command, clock.instant())
        return status(userId)
    }

    private fun persistActivation(
        user: com.snabbit.prototype.user.domain.UserProfile,
        sourceReference: String,
        command: ActivatePlusCommand,
        now: Instant,
    ) {
        val subscription = subscriptions.save(
            PlusSubscription(
                UUID.randomUUID(), user.id, sourceReference, command.startsAt, command.expiresAt, now,
            ),
        )
        if (command.passCount > 0) {
            passes.saveAll(
                (1..command.passCount).map {
                    PlusPass(
                        UUID.randomUUID(), subscription.id, user.id,
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
        outbox.append(
            user.id,
            "PlusSubscriptionActivated",
            mapOf(
                "userId" to user.id,
                "subscriptionId" to subscription.id,
                "startsAt" to command.startsAt,
                "expiresAt" to command.expiresAt,
                "passCount" to command.passCount,
            ),
            now,
        )
    }

    @Transactional
    fun reservePass(
        userId: UUID,
        bookingId: UUID,
        serviceCode: String,
        grossTotalMinor: Long,
        holdUntil: Instant,
    ): PassReservationSnapshot {
        if (grossTotalMinor < 100 || grossTotalMinor % 100 != 0L) {
            throw PlusRequestRejected("Gross total must use positive whole-rupee increments")
        }
        if (grossTotalMinor == 100L) return noDiscount(bookingId, grossTotalMinor)
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

    @Transactional
    fun restorePass(bookingId: UUID) {
        passes.findByBookingForUpdate(bookingId)?.restoreAfterPlatformCancellation()
    }

    private fun reservation(pass: PlusPass, bookingId: UUID, gross: Long): PassReservationSnapshot {
        val discount = min(pass.discountMinor, gross - 100)
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
        if (command.passDiscountMinor < 0 || command.passDiscountMinor % 100 != 0L ||
            (command.passCount > 0 && command.passDiscountMinor < 100)
        ) {
            throw PlusRequestRejected("Pass discount must use non-negative whole-rupee increments")
        }
    }
}
