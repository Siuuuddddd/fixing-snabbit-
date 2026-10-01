package com.snabbit.prototype.booking.application

import com.snabbit.prototype.booking.config.BookingProperties
import com.snabbit.prototype.booking.persistence.SlotHoldRepository
import com.snabbit.prototype.booking.persistence.WorkerOfferRepository
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock

@Component
class BookingExpiryScheduler(
    private val offerRepository: WorkerOfferRepository,
    private val holdRepository: SlotHoldRepository,
    private val processor: BookingExpiryProcessor,
    private val properties: BookingProperties,
    private val clock: Clock,
) {
    @Scheduled(fixedDelayString = "\${booking.expiry-scan-delay}")
    fun expireStaleReservations() {
        val now = clock.instant()
        processor.expireOffers(offerRepository.findExpiredIds(now, properties.expiryBatchSize), now)
        processor.expireHolds(holdRepository.findExpiredIds(now, properties.expiryBatchSize), now)
    }
}
