package com.snabbit.prototype.mobile.data.booking

import com.snabbit.prototype.mobile.data.booking.remote.BookingApi
import com.snabbit.prototype.mobile.data.booking.remote.BookingDto
import com.snabbit.prototype.mobile.data.booking.remote.MatchAndHoldDto
import com.snabbit.prototype.mobile.data.booking.remote.StartPaymentDto
import com.snabbit.prototype.mobile.data.booking.remote.HomeContactRequestDto
import com.snabbit.prototype.mobile.data.booking.remote.VerifyArrivalOtpDto
import com.snabbit.prototype.mobile.data.booking.remote.WorkerOptionDto
import com.snabbit.prototype.mobile.data.booking.remote.WorkerSelectionDto
import com.snabbit.prototype.mobile.data.network.apiCall
import com.snabbit.prototype.mobile.domain.booking.Booking
import com.snabbit.prototype.mobile.domain.booking.BookingAvailability
import com.snabbit.prototype.mobile.domain.booking.BookingDraft
import com.snabbit.prototype.mobile.domain.booking.BookingPayment
import com.snabbit.prototype.mobile.domain.booking.BookingRepository
import com.snabbit.prototype.mobile.domain.booking.BookingStatus
import com.snabbit.prototype.mobile.domain.booking.BookingVisit
import com.snabbit.prototype.mobile.domain.booking.VisitAvailability
import com.snabbit.prototype.mobile.domain.booking.ArrivalOtpStatus
import com.snabbit.prototype.mobile.domain.booking.HomeContact
import com.snabbit.prototype.mobile.domain.booking.WorkerOption
import com.snabbit.prototype.mobile.domain.booking.WorkerOptions
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

    override suspend fun workerOptions(bookingId: String, visitIndex: Int) = apiCall(json) {
        api.workerOptions(bookingId, visitIndex).let { response ->
            fun map(options: List<WorkerOptionDto>, favourite: Boolean) = options.map { option ->
                WorkerOption(
                    option.workerId, option.firstName, option.tier, option.averageRating, option.ratingCount,
                    option.distanceKm, option.tierSurchargeMinor,
                    option.visits.singleOrNull { it.visitIndex == visitIndex }?.priceMinor ?: 0L,
                    favourite,
                )
            }
            WorkerOptions(visitIndex, map(response.favourites, true), map(response.others, false))
        }
    }

    override suspend fun selectWorker(bookingId: String, visitIndex: Int, workerId: String) = apiCall(json) {
        api.selectWorker(bookingId, WorkerSelectionDto(visitIndex, workerId)).toDomain()
    }

    override suspend fun updateHomeContact(bookingId: String, name: String?, phone: String?) = apiCall(json) {
        val other = !name.isNullOrBlank() || !phone.isNullOrBlank()
        api.updateHomeContact(bookingId, HomeContactRequestDto(if (other) "OTHER" else "SELF", name, phone)).let {
            HomeContact(it.name, it.maskedPhone, it.isBooker)
        }
    }

    override suspend fun verifyArrivalOtp(bookingId: String, visitIndex: Int, code: String) = apiCall(json) {
        api.verifyArrivalOtp(bookingId, visitIndex, VerifyArrivalOtpDto(code)).let {
            ArrivalOtpStatus(it.status, it.remainingSends, it.retryAfterSeconds)
        }
    }

    override suspend fun resendArrivalOtp(bookingId: String, visitIndex: Int) = apiCall(json) {
        api.resendArrivalOtp(bookingId, visitIndex).let {
            ArrivalOtpStatus(it.status, it.remainingSends, it.retryAfterSeconds)
        }
    }

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
        id = bookingId,
        status = runCatching { BookingStatus.valueOf(status) }.getOrDefault(BookingStatus.UNKNOWN),
        quotedTotalMinor = quotedTotalMinor,
        grossTotalMinor = grossTotalMinor,
        plusDiscountMinor = plusDiscountMinor,
        visits = visits.map {
            BookingVisit(
                visitIndex = it.visitIndex,
                workerId = it.workerId,
                startsAt = it.startsAt,
                endsAt = it.endsAt,
                offerStatus = it.offerStatus,
                jobStatus = it.jobStatus,
                durationMinutes = it.durationMinutes,
                arrivalDueAt = it.arrivalDueAt,
                selectedWorkerName = it.selectedWorkerName,
                selectedWorkerTier = it.selectedWorkerTier,
                selectedWorkerRating = it.selectedWorkerRating,
            )
        },
        cancellationType = cancellationType,
        cancellationReason = cancellationReason,
        cancellationFeeMinor = cancellationFeeMinor,
        refundAmountMinor = refundAmountMinor,
        serverNow = serverNow,
        maskedHomeContactPhone = maskedHomeContactPhone,
    )
}
