package com.snabbit.prototype.booking.service

import com.snabbit.prototype.booking.domain.enums.WorkerOfferStatus

import com.snabbit.prototype.booking.domain.enums.SlotHoldStatus

import com.snabbit.prototype.booking.domain.enums.BookingStatus

import com.snabbit.prototype.booking.domain.SlotHold
import com.snabbit.prototype.booking.repository.BookingRepository
import com.snabbit.prototype.booking.repository.SlotHoldRepository
import com.snabbit.prototype.booking.repository.WorkerOfferRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class BookingExpiryProcessor(
    private val bookingRepository: BookingRepository,
    private val holdRepository: SlotHoldRepository,
    private val offerRepository: WorkerOfferRepository,
    private val outboxWriter: OutboxWriter,
) {
    @Transactional
    fun expireOffers(offerIds: List<UUID>, now: Instant) {
        offerIds.forEach { offerId ->
            val observed = offerRepository.findById(offerId).orElse(null) ?: return@forEach
            val booking = bookingRepository.findForUpdate(observed.bookingId) ?: return@forEach
            val offer = offerRepository.findForUpdate(observed.bookingId, offerId) ?: return@forEach
            if (offer.status != WorkerOfferStatus.OFFERED || offer.expiresAt.isAfter(now)) return@forEach

            offer.expire(now)
            if (booking.status == BookingStatus.AWAITING_WORKER) {
                booking.markWorkerUnavailable(now)
                holdRepository.findAllByBookingIdOrderByVisitIndex(booking.id).forEach(SlotHold::release)
                offerRepository.findAllByBookingIdOrderByCreatedAt(booking.id)
                    .filter { it.status == WorkerOfferStatus.OFFERED }
                    .forEach { it.expire(now) }
                outboxWriter.append(
                    booking.id,
                    "WorkerOfferExpired",
                    mapOf("bookingId" to booking.id, "offerId" to offer.id, "workerId" to offer.workerId),
                    now,
                )
            }
        }
    }

    @Transactional
    fun expireHolds(holdIds: List<UUID>, now: Instant) {
        holdIds.forEach { holdId ->
            val observed = holdRepository.findById(holdId).orElse(null) ?: return@forEach
            val booking = bookingRepository.findForUpdate(observed.bookingId) ?: return@forEach
            val hold = holdRepository.findForUpdate(holdId) ?: return@forEach
            if (hold.status != SlotHoldStatus.HELD || hold.expiresAt.isAfter(now)) return@forEach
            if (booking.status != BookingStatus.AWAITING_WORKER && booking.status != BookingStatus.AWAITING_PAYMENT) {
                hold.expire()
                return@forEach
            }

            holdRepository.findAllByBookingIdOrderByVisitIndex(booking.id).forEach(SlotHold::expire)
            offerRepository.findAllByBookingIdOrderByCreatedAt(booking.id)
                .filter { it.status == WorkerOfferStatus.OFFERED }
                .forEach { it.expire(now) }
            booking.expire(now)
            outboxWriter.append(
                booking.id,
                "BookingExpired",
                mapOf("bookingId" to booking.id, "expiredAt" to now),
                now,
            )
        }
    }
}
