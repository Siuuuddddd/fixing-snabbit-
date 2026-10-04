package com.snabbit.prototype.mobile.ui.booking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snabbit.prototype.mobile.domain.booking.BookingAvailability
import com.snabbit.prototype.mobile.domain.booking.BookingDraft
import com.snabbit.prototype.mobile.domain.booking.BookingRepository
import com.snabbit.prototype.mobile.domain.household.HouseholdPreferences
import com.snabbit.prototype.mobile.domain.household.HouseholdRepository
import com.snabbit.prototype.mobile.domain.wallet.WalletBalance
import com.snabbit.prototype.mobile.domain.wallet.WalletRepository
import com.snabbit.prototype.mobile.ui.common.UiDataError
import com.snabbit.prototype.mobile.ui.common.toUiDataError
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BookingSummaryUiState(
    val draft: BookingDraft? = null,
    val isLoading: Boolean = true,
    val isConfirming: Boolean = false,
    val availability: BookingAvailability? = null,
    val household: HouseholdPreferences? = null,
    val wallet: WalletBalance? = null,
    val useWallet: Boolean = true,
    val createdBookingId: String? = null,
    val error: UiDataError? = null,
) {
    val estimatedTotalMinor get() = availability?.visits?.sumOf { it.lowestPriceMinor ?: 0L } ?: 0L
    val estimatedWalletMinor get() = if (useWallet) minOf(wallet?.availableMinor ?: 0L, estimatedTotalMinor) else 0L
    val estimatedUpiMinor get() = estimatedTotalMinor - estimatedWalletMinor
}

class BookingSummaryViewModel(
    private val draft: BookingDraft,
    private val bookingRepository: BookingRepository,
    private val householdRepository: HouseholdRepository,
    private val walletRepository: WalletRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main,
    private val idempotencyKey: String = UUID.randomUUID().toString(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(BookingSummaryUiState(draft = draft))
    val uiState: StateFlow<BookingSummaryUiState> = _uiState.asStateFlow()
    init { load() }

    fun retry() = load()
    fun consumeCreatedBooking() = _uiState.update { it.copy(createdBookingId = null) }
    fun setUseWallet(value: Boolean) = _uiState.update { it.copy(useWallet = value) }

    fun confirm() {
        val state = _uiState.value
        if (state.isConfirming || state.availability?.allVisitsAvailable != true) return
        viewModelScope.launch(dispatcher) {
            _uiState.update { it.copy(isConfirming = true, error = null) }
            runCatching { bookingRepository.matchAndHold(draft, idempotencyKey) }
                .onSuccess { booking -> _uiState.update { it.copy(isConfirming = false, createdBookingId = booking.id) } }
                .onFailure { failure -> _uiState.update { it.copy(isConfirming = false, error = failure.toUiDataError()) } }
        }
    }

    private fun load() = viewModelScope.launch(dispatcher) {
        _uiState.update { it.copy(isLoading = true, error = null) }
        runCatching {
            val availability = async { bookingRepository.availability(draft) }
            val household = async { householdRepository.preferences() }
            val wallet = async { walletRepository.balance() }
            Triple(availability.await(), household.await(), wallet.await())
        }.onSuccess { (availability, household, wallet) ->
            _uiState.update { it.copy(isLoading = false, availability = availability, household = household, wallet = wallet) }
        }.onFailure { failure -> _uiState.update { it.copy(isLoading = false, error = failure.toUiDataError()) } }
    }
}
