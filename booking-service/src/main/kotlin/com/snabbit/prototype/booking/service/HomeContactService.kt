package com.snabbit.prototype.booking.service

import com.snabbit.prototype.booking.domain.Booking
import com.snabbit.prototype.booking.domain.enums.HomeContactMode
import com.snabbit.prototype.booking.dto.request.UpdateHomeContactRequest
import com.snabbit.prototype.booking.dto.response.HomeContactSnapshot
import com.snabbit.prototype.booking.exception.BookingNotFound
import com.snabbit.prototype.booking.exception.CustomerAccessDenied
import com.snabbit.prototype.booking.exception.CustomerPhoneRequired
import com.snabbit.prototype.booking.exception.HomeContactResolutionUnavailable
import com.snabbit.prototype.booking.exception.HomeContactValidationException
import com.snabbit.prototype.booking.exception.InvalidBookingState
import com.snabbit.prototype.booking.repository.BookingRepository
import com.snabbit.prototype.platform.IndianPhoneNumber
import java.time.Clock
import java.util.Locale
import java.util.UUID
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class HomeContactService(
    private val bookings: BookingRepository,
    private val contacts: UserContactClient,
    private val clock: Clock,
) {
    private val allowedName = Regex("^[\\p{L} .'-]+$")

    @Transactional
    fun update(
        bookingId: UUID,
        authenticatedCustomerId: UUID,
        request: UpdateHomeContactRequest?,
    ): HomeContactSnapshot {
        val booking = ownedMutableBooking(bookingId, authenticatedCustomerId)
        val mode = parseMode(request?.mode)
        val resolved = when (mode) {
            HomeContactMode.SELF -> resolveBooker(authenticatedCustomerId)
            HomeContactMode.OTHER -> resolveOther(request)
        }
        apply(booking, resolved)
        return snapshot(booking)
    }

    @Transactional
    fun ensureResolved(bookingId: UUID, authenticatedCustomerId: UUID): HomeContactSnapshot {
        val booking = ownedMutableBooking(bookingId, authenticatedCustomerId)
        if (!booking.hasResolvedHomeContact()) apply(booking, resolveBooker(authenticatedCustomerId))
        return snapshot(booking)
    }

    @Transactional
    fun ensureResolvedForConfirmation(bookingId: UUID) {
        val booking = bookings.findForUpdate(bookingId) ?: throw BookingNotFound(bookingId)
        if (booking.homeContactFrozenAt != null) return
        if (booking.status.name != "AWAITING_PAYMENT") {
            throw InvalidBookingState("Booking is not awaiting payment confirmation")
        }
        if (!booking.hasResolvedHomeContact()) apply(booking, resolveBooker(booking.customerId))
    }

    fun ensureResolvedForArrival(booking: Booking, now: java.time.Instant) {
        if (booking.hasResolvedHomeContact()) return
        val contact = resolveBooker(booking.customerId)
        try {
            booking.backfillFrozenHomeContact(contact.name, contact.phone, now)
        } catch (error: IllegalStateException) {
            throw InvalidBookingState(error.message.orEmpty())
        }
    }

    private fun ownedMutableBooking(bookingId: UUID, customerId: UUID): Booking {
        val booking = bookings.findForUpdate(bookingId) ?: throw BookingNotFound(bookingId)
        if (booking.customerId != customerId) throw CustomerAccessDenied()
        if (booking.homeContactFrozenAt != null) throw InvalidBookingState("Home contact is frozen after confirmation")
        if (booking.status.name !in setOf("AWAITING_WORKER", "AWAITING_PAYMENT")) {
            throw InvalidBookingState("Home contact can only be changed before payment confirmation")
        }
        return booking
    }

    private fun resolveBooker(customerId: UUID): ResolvedContact {
        val contact = contacts.contact(customerId)
        if (contact.userId != customerId) throw HomeContactResolutionUnavailable("Booker contact is temporarily unavailable")
        val name = contact.displayName.trim().takeIf { it.length in 2..60 }
            ?: throw HomeContactResolutionUnavailable("Booker contact is temporarily unavailable")
        val rawPhone = contact.phoneNumber?.takeIf(String::isNotBlank) ?: throw CustomerPhoneRequired()
        val phone = try {
            IndianPhoneNumber.normalize(rawPhone)
        } catch (_: IllegalArgumentException) {
            throw HomeContactResolutionUnavailable("Booker contact is temporarily unavailable")
        }
        return ResolvedContact(name, phone, true)
    }

    private fun resolveOther(request: UpdateHomeContactRequest?): ResolvedContact {
        val name = request?.name?.takeIf(String::isNotBlank)
            ?: throw HomeContactValidationException("name", "Name is required for another home contact")
        val phone = request.phone?.takeIf(String::isNotBlank)
            ?: throw HomeContactValidationException("phone", "Phone is required for another home contact")
        val normalized = try {
            IndianPhoneNumber.normalize(phone)
        } catch (_: IllegalArgumentException) {
            throw HomeContactValidationException("phone", "Enter a valid Indian mobile number")
        }
        return ResolvedContact(validateName(name), normalized, false)
    }

    private fun validateName(raw: String): String {
        val name = raw.trim()
        if (name.length !in 2..60) {
            throw HomeContactValidationException("name", "Name must contain 2 to 60 characters")
        }
        if (!name.matches(allowedName)) {
            throw HomeContactValidationException("name", "Name may contain only letters, spaces, period, apostrophe and hyphen")
        }
        return name
    }

    private fun parseMode(raw: String?): HomeContactMode {
        if (raw.isNullOrBlank()) return HomeContactMode.SELF
        return runCatching { HomeContactMode.valueOf(raw.trim().uppercase(Locale.ROOT)) }
            .getOrElse { throw HomeContactValidationException("mode", "Mode must be SELF or OTHER") }
    }

    private fun apply(booking: Booking, contact: ResolvedContact) {
        try {
            booking.updateHomeContact(contact.name, contact.phone, contact.isBooker, clock.instant())
        } catch (error: IllegalStateException) {
            throw InvalidBookingState(error.message.orEmpty())
        }
    }

    private fun snapshot(booking: Booking): HomeContactSnapshot {
        val phone = requireNotNull(booking.homeContactPhoneE164)
        return HomeContactSnapshot(
            name = requireNotNull(booking.homeContactName),
            maskedPhone = "••••••${phone.takeLast(4)}",
            isBooker = requireNotNull(booking.homeContactIsBooker),
        )
    }

    private data class ResolvedContact(val name: String, val phone: String, val isBooker: Boolean)
}
