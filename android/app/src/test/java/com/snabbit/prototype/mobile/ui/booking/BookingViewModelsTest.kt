package com.snabbit.prototype.mobile.ui.booking

import com.snabbit.prototype.mobile.domain.booking.Booking
import com.snabbit.prototype.mobile.domain.booking.BookingAvailability
import com.snabbit.prototype.mobile.domain.booking.BookingDraft
import com.snabbit.prototype.mobile.domain.booking.BookingPayment
import com.snabbit.prototype.mobile.domain.booking.BookingRepository
import com.snabbit.prototype.mobile.domain.booking.BookingStatus
import com.snabbit.prototype.mobile.domain.booking.BookingType
import com.snabbit.prototype.mobile.domain.booking.VisitAvailability
import com.snabbit.prototype.mobile.domain.common.DataFailure
import com.snabbit.prototype.mobile.domain.common.DataFailureReason
import com.snabbit.prototype.mobile.domain.household.HouseholdPreferences
import com.snabbit.prototype.mobile.domain.household.HouseholdRepository
import com.snabbit.prototype.mobile.domain.wallet.WalletBalance
import com.snabbit.prototype.mobile.domain.wallet.WalletLedgerEntry
import com.snabbit.prototype.mobile.domain.wallet.WalletRepository
import com.snabbit.prototype.mobile.domain.wallet.WalletTopUp
import com.snabbit.prototype.mobile.ui.common.UiDataErrorReason
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BookingViewModelsTest {
    private val fixedClock = Clock.fixed(Instant.parse("2026-10-04T02:30:00Z"), ZoneId.of("Asia/Kolkata"))

    @Test
    fun `slot selection sends exact multi-day draft and exposes availability`() = runTest {
        val repository = FakeBookingRepository()
        val viewModel = SlotSelectionViewModel("service", "address", 2, repository, fixedClock, StandardTestDispatcher(testScheduler))
        viewModel.selectType(BookingType.MULTI_DAY)
        viewModel.toggleDate(LocalDate.of(2026, 10, 5))
        viewModel.toggleDate(LocalDate.of(2026, 10, 6))
        viewModel.selectTime(LocalTime.of(10, 0))
        viewModel.checkAvailability()
        runCurrent()

        assertEquals(BookingType.MULTI_DAY, repository.lastDraft?.bookingType)
        assertEquals(2, repository.lastDraft?.startsAt?.size)
        assertTrue(viewModel.uiState.value.canContinue)
    }

    @Test
    fun `multi-day requires at least two distinct days`() = runTest {
        val repository = FakeBookingRepository()
        val viewModel = SlotSelectionViewModel("service", "address", 2, repository, fixedClock, StandardTestDispatcher(testScheduler))
        viewModel.selectType(BookingType.MULTI_DAY)
        viewModel.toggleDate(LocalDate.of(2026, 10, 5))
        viewModel.selectTime(LocalTime.of(10, 0))
        viewModel.checkAvailability()

        assertEquals(null, repository.lastDraft)
        assertEquals(SlotValidationError.MULTI_DAY_COUNT, viewModel.uiState.value.validationError)
    }

    @Test
    fun `household saves backend enum values`() = runTest {
        val repository = FakeHouseholdRepository()
        val viewModel = HouseholdPreferencesViewModel(repository, StandardTestDispatcher(testScheduler))
        runCurrent()
        viewModel.setPets(true)
        viewModel.toggleLanguage("HINDI")
        viewModel.toggleConstraint("MASK_REQUIRED")
        viewModel.save()
        runCurrent()

        assertTrue(repository.saved?.hasPets == true)
        assertTrue("HINDI" in requireNotNull(repository.saved).preferredLanguages)
        assertTrue(viewModel.uiState.value.saved)
    }

    @Test
    fun `summary computes estimate and creates hold with stable payment preference`() = runTest {
        val bookings = FakeBookingRepository()
        val viewModel = BookingSummaryViewModel(
            sampleDraft(), bookings, FakeHouseholdRepository(), FakeWalletRepository(),
            StandardTestDispatcher(testScheduler), "summary-key",
        )
        runCurrent()

        assertEquals(5000, viewModel.uiState.value.estimatedWalletMinor)
        assertEquals(5000, viewModel.uiState.value.estimatedUpiMinor)
        viewModel.confirm()
        runCurrent()
        assertEquals("summary-key", bookings.matchKey)
        assertEquals("booking", viewModel.uiState.value.createdBookingId)
    }

    @Test
    fun `summary keeps booking available when configured wallet belongs to another customer`() = runTest {
        val viewModel = BookingSummaryViewModel(
            sampleDraft(), FakeBookingRepository(), FakeHouseholdRepository(), ForbiddenWalletRepository(),
            StandardTestDispatcher(testScheduler), "summary-key",
        )
        runCurrent()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(null, viewModel.uiState.value.error)
        assertTrue(viewModel.uiState.value.availability?.allVisitsAvailable == true)
        assertEquals(null, viewModel.uiState.value.wallet)
        assertFalse(viewModel.uiState.value.useWallet)
    }

    @Test
    fun `summary exposes booking API failure as recoverable error state`() = runTest {
        val viewModel = BookingSummaryViewModel(
            sampleDraft(), FailingAvailabilityRepository(), FakeHouseholdRepository(), FakeWalletRepository(),
            StandardTestDispatcher(testScheduler), "summary-key",
        )
        runCurrent()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(UiDataErrorReason.SERVER, viewModel.uiState.value.error?.reason)
        assertEquals(null, viewModel.uiState.value.availability)
    }

    @Test
    fun `finding worker starts authoritative payment only after worker accepts`() = runTest {
        val bookings = FakeBookingRepository(status = BookingStatus.AWAITING_PAYMENT)
        val viewModel = FindingWorkerViewModel(
            "booking", "wallet", true, bookings, StandardTestDispatcher(testScheduler), 60_000, "payment-key",
        )
        runCurrent()

        assertEquals("payment-key", bookings.paymentKey)
        assertEquals(7000L, viewModel.uiState.value.payment?.walletAmountMinor)
        assertEquals(3000L, viewModel.uiState.value.payment?.onlineAmountMinor)
        advanceTimeBy(60_000)
        runCurrent()
    }
}

private class FakeBookingRepository(
    private val status: BookingStatus = BookingStatus.AWAITING_WORKER,
) : BookingRepository {
    var lastDraft: BookingDraft? = null
    var matchKey: String? = null
    var paymentKey: String? = null
    private var bookingCalls = 0

    override suspend fun availability(draft: BookingDraft): BookingAvailability {
        lastDraft = draft
        return BookingAvailability(
            draft.durationHours * 60, true, true,
            draft.startsAt.map { VisitAvailability(it, true, 3, 10_000L / draft.startsAt.size) },
        )
    }
    override suspend fun matchAndHold(draft: BookingDraft, idempotencyKey: String): Booking {
        matchKey = idempotencyKey
        return booking(status)
    }
    override suspend fun booking(bookingId: String): Booking {
        bookingCalls++
        return booking(if (status == BookingStatus.AWAITING_PAYMENT && bookingCalls > 1) BookingStatus.CONFIRMED else status)
    }
    override suspend fun cancelHold(bookingId: String) = booking(BookingStatus.CANCELLED)
    override suspend fun startPayment(bookingId: String, walletId: String, useWallet: Boolean, idempotencyKey: String): BookingPayment {
        paymentKey = idempotencyKey
        return BookingPayment("payment", 10_000, 7_000, 3_000, "PENDING", "order")
    }
}

private class FakeHouseholdRepository : HouseholdRepository {
    var saved: HouseholdPreferences? = null
    override suspend fun preferences() = HouseholdPreferences()
    override suspend fun update(preferences: HouseholdPreferences) = preferences.also { saved = it }
}

private class FakeWalletRepository : WalletRepository {
    override suspend fun balance() = WalletBalance("wallet", "INR", 5_000, 0, 5_000, 0)
    override suspend fun createTopUp(amountMinor: Long, idempotencyKey: String) =
        WalletTopUp("top-up", "wallet", amountMinor, "PENDING", "order")
    override suspend fun history(): List<WalletLedgerEntry> = emptyList()
    override suspend fun recordPrototypeEntry(entry: WalletLedgerEntry) = Unit
}

private class ForbiddenWalletRepository : WalletRepository {
    override suspend fun balance(): WalletBalance? =
        throw DataFailure(DataFailureReason.FORBIDDEN, "Authenticated customer does not own this wallet")
    override suspend fun createTopUp(amountMinor: Long, idempotencyKey: String) = error("unused")
    override suspend fun history(): List<WalletLedgerEntry> = emptyList()
    override suspend fun recordPrototypeEntry(entry: WalletLedgerEntry) = Unit
}

private class FailingAvailabilityRepository : BookingRepository {
    override suspend fun availability(draft: BookingDraft): BookingAvailability =
        throw DataFailure(DataFailureReason.SERVER, "Booking availability is temporarily unavailable")
    override suspend fun matchAndHold(draft: BookingDraft, idempotencyKey: String) = error("unused")
    override suspend fun booking(bookingId: String) = error("unused")
    override suspend fun cancelHold(bookingId: String) = error("unused")
    override suspend fun startPayment(
        bookingId: String,
        walletId: String,
        useWallet: Boolean,
        idempotencyKey: String,
    ) = error("unused")
}

private fun sampleDraft() = BookingDraft(
    "service", "address", BookingType.SCHEDULED, 2, listOf("2026-10-05T04:30:00Z"),
)

private fun booking(status: BookingStatus) = Booking("booking", status, 10_000, 10_000, 0, emptyList())
