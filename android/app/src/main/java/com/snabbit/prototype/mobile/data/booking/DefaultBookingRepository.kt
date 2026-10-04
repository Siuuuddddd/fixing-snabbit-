package com.snabbit.prototype.mobile.data.booking

import com.snabbit.prototype.mobile.data.booking.remote.BookingApi
import com.snabbit.prototype.mobile.data.booking.remote.BookingDto
import com.snabbit.prototype.mobile.data.booking.remote.MatchAndHoldDto
import com.snabbit.prototype.mobile.data.booking.remote.StartPaymentDto
import com.snabbit.prototype.mobile.data.network.apiCall
import com.snabbit.prototype.mobile.domain.booking.Booking
import com.snabbit.prototype.mobile.domain.booking.BookingAvailability
import com.snabbit.prototype.mobile.domain.booking.BookingDraft
import com.snabbit.prototype.mobile.domain.booking.BookingPayment
import com.snabbit.prototype.mobile.domain.booking.BookingRepository
import com.snabbit.prototype.mobile.domain.booking.BookingStatus
import com.snabbit.prototype.mobile.domain.booking.BookingVisit
import com.snabbit.prototype.mobile.domain.booking.VisitAvailability
import com.snabbit.prototype.mobile.domain.bookings.KnownBookingDataSource
import com.snabbit.prototype.mobile.domain.common.DataFailure
import com.snabbit.prototype.mobile.domain.common.DataFailureReason
import com.snabbit.prototype.mobile.domain.session.SessionIdentityProvider
import kotlinx.serialization.json.Json

class DefaultBookingRepository(
    private val api: BookingApi,
    private val identity: SessionIdentityProvider,
    private val knownBookings: KnownBookingDataSource,
    private val json: Json,
) : BookingRepository {
    override suspend fun availability(draft: BookingDraft) = apiCall(json) {
        api.availability(draft.toDto()).let { response ->
            BookingAvailability(
                response.durationMinutes,
                response.allVisitsAvailable,
                response.sameWorkerAvailableForAllVisits,
                response.visits.map { VisitAvailability(it.startsAt, it.available, it.compatibleWorkerCount, it.lowestPriceMinor) },
            )
        }
    }

    override suspend fun matchAndHold(draft: BookingDraft, idempotencyKey: String) = apiCall(json) {
        api.matchAndHold(idempotencyKey, draft.toDto()).toDomain().also { booking ->
            // Local history must never turn an authoritative booking success into a client-visible failure.
            runCatching { knownBookings.remember(booking.id) }
        }
    }

    override suspend fun booking(bookingId: String) = apiCall(json) { api.booking(bookingId).toDomain() }
    override suspend fun cancelHold(bookingId: String) = apiCall(json) { api.cancelHold(bookingId).toDomain() }

    override suspend fun startPayment(bookingId: String, walletId: String, useWallet: Boolean, idempotencyKey: String) = apiCall(json) {
        api.startPayment(bookingId, idempotencyKey, StartPaymentDto(walletId, useWallet)).let {
            BookingPayment(it.paymentId, it.totalAmountMinor, it.walletAmountMinor, it.onlineAmountMinor, it.status, it.providerOrderId)
        }
    }

    private fun BookingDraft.toDto() = MatchAndHoldDto(
        customerId = identity.customerId() ?: throw DataFailure(DataFailureReason.UNAUTHORIZED),
        serviceId = serviceId,
        bookingType = bookingType.name,
        durationHours = durationHours.toLong(),
        addressId = addressId,
        startsAt = startsAt,
    )

    private fun BookingDto.toDomain() = Booking(
        bookingId,
        runCatching { BookingStatus.valueOf(status) }.getOrDefault(BookingStatus.UNKNOWN),
        quotedTotalMinor,
        grossTotalMinor,
        plusDiscountMinor,
        visits.map { BookingVisit(it.visitIndex, it.workerId, it.startsAt, it.endsAt, it.offerStatus, it.jobStatus) },
        cancellationType,
        cancellationReason,
        cancellationFeeMinor,
        refundAmountMinor,
    )
}
