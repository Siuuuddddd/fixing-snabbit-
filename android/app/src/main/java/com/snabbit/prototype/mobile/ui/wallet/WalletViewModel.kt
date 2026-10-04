package com.snabbit.prototype.mobile.ui.wallet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snabbit.prototype.mobile.domain.payment.CheckoutPurpose
import com.snabbit.prototype.mobile.domain.payment.CheckoutRequest
import com.snabbit.prototype.mobile.domain.payment.CheckoutResult
import com.snabbit.prototype.mobile.domain.payment.PaymentCheckoutCoordinator
import com.snabbit.prototype.mobile.domain.wallet.LedgerEntryState
import com.snabbit.prototype.mobile.domain.wallet.WalletBalance
import com.snabbit.prototype.mobile.domain.wallet.WalletLedgerEntry
import com.snabbit.prototype.mobile.domain.wallet.WalletRepository
import com.snabbit.prototype.mobile.ui.common.UiDataError
import com.snabbit.prototype.mobile.ui.common.toUiDataError
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AddMoneyPhase { IDLE, CREATING_ORDER, CHECKOUT, VERIFYING, CONFIRMED, FAILED }

data class WalletUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val balance: WalletBalance? = null,
    val history: List<WalletLedgerEntry> = emptyList(),
    val amountRupees: String = "500",
    val phase: AddMoneyPhase = AddMoneyPhase.IDLE,
    val message: String? = null,
    val error: UiDataError? = null,
)

class WalletViewModel(
    private val repository: WalletRepository,
    private val checkout: PaymentCheckoutCoordinator,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main,
    private val verificationDelayMillis: Long = 3_000,
    private val verificationAttempts: Int = 20,
) : ViewModel() {
    private val _uiState = MutableStateFlow(WalletUiState())
    val uiState: StateFlow<WalletUiState> = _uiState.asStateFlow()
    private var activeRequestId: String? = null
    private var activeEntryId: String? = null
    private var activeAmountMinor = 0L
    private var baselineLedgerMinor = 0L
    private var idempotencyKey = UUID.randomUUID().toString()

    init {
        load(false)
        viewModelScope.launch(dispatcher) {
            checkout.results.collect { result ->
                if (result.requestId != activeRequestId) return@collect
                when (result) {
                    is CheckoutResult.ClientSuccess -> verifyBackend()
                    is CheckoutResult.ClientFailure -> fail(
                        result.description?.takeIf { it.isNotBlank() }
                            ?.let { "$it Your wallet was not changed." }
                            ?: "UPI payment was not completed. Your wallet was not changed.",
                    )
                }
            }
        }
    }

    fun refresh() = load(true)
    fun retry() = load(false)
    fun updateAmount(value: String) {
        if (value.all(Char::isDigit) && value.length <= 7) {
            _uiState.update { it.copy(amountRupees = value, phase = AddMoneyPhase.IDLE, message = null) }
            idempotencyKey = UUID.randomUUID().toString()
        }
    }

    fun addMoney() {
        if (_uiState.value.phase in setOf(AddMoneyPhase.CREATING_ORDER, AddMoneyPhase.CHECKOUT, AddMoneyPhase.VERIFYING)) return
        val amountMinor = _uiState.value.amountRupees.toLongOrNull()?.times(100)
        if (amountMinor == null || amountMinor < 100) {
            _uiState.update { it.copy(phase = AddMoneyPhase.FAILED, message = "Enter at least ₹1 in whole rupees.") }
            return
        }
        viewModelScope.launch(dispatcher) {
            _uiState.update { it.copy(phase = AddMoneyPhase.CREATING_ORDER, message = "Creating a secure payment order…", error = null) }
            runCatching { repository.createTopUp(amountMinor, idempotencyKey) }
                .onSuccess { topUp ->
                    val providerOrder = topUp.providerOrderId
                    if (providerOrder.isNullOrBlank()) {
                        fail("The backend did not return a payment order. Your wallet was not changed.")
                        return@onSuccess
                    }
                    activeRequestId = UUID.randomUUID().toString()
                    activeEntryId = topUp.topUpId
                    activeAmountMinor = topUp.amountMinor
                    baselineLedgerMinor = _uiState.value.balance?.ledgerBalanceMinor ?: 0L
                    record(LedgerEntryState.PENDING, "Add money")
                    _uiState.update { it.copy(phase = AddMoneyPhase.CHECKOUT, message = "Complete the UPI payment in Razorpay.") }
                    checkout.request(
                        CheckoutRequest(activeRequestId!!, CheckoutPurpose.WALLET_TOP_UP, providerOrder, topUp.amountMinor, "Add money to Snabbit Wallet")
                    )
                }
                .onFailure { failure ->
                    _uiState.update { it.copy(phase = AddMoneyPhase.FAILED, message = null, error = failure.toUiDataError()) }
                }
        }
    }

    fun checkAgain() {
        if (activeEntryId != null) viewModelScope.launch(dispatcher) { verifyBackend() }
    }

    private suspend fun verifyBackend() {
        _uiState.update {
            it.copy(phase = AddMoneyPhase.VERIFYING, message = "UPI payment submitted. Waiting for backend confirmation—your displayed balance is unchanged.")
        }
        repeat(verificationAttempts) { attempt ->
            val current = runCatching { repository.balance() }.getOrNull()
            if (current != null && current.ledgerBalanceMinor >= baselineLedgerMinor + activeAmountMinor) {
                record(LedgerEntryState.CONFIRMED, "Add money")
                _uiState.update {
                    it.copy(balance = current, history = repository.history(), phase = AddMoneyPhase.CONFIRMED, message = "Backend confirmed the UPI payment and updated your wallet.")
                }
                idempotencyKey = UUID.randomUUID().toString()
                return
            }
            if (attempt < verificationAttempts - 1) delay(verificationDelayMillis)
        }
        _uiState.update { it.copy(phase = AddMoneyPhase.VERIFYING, message = "Still awaiting backend confirmation. Your displayed balance remains unchanged.") }
    }

    private suspend fun fail(message: String) {
        record(LedgerEntryState.FAILED, "Add money")
        _uiState.update { it.copy(phase = AddMoneyPhase.FAILED, message = message) }
    }

    private suspend fun record(state: LedgerEntryState, description: String) {
        val id = activeEntryId ?: return
        repository.recordPrototypeEntry(WalletLedgerEntry(id, activeAmountMinor, state, Instant.now().toString(), description))
        _uiState.update { it.copy(history = repository.history()) }
    }

    private fun load(refreshing: Boolean) = viewModelScope.launch(dispatcher) {
        _uiState.update { it.copy(isLoading = !refreshing && it.balance == null, isRefreshing = refreshing, error = null) }
        runCatching { repository.balance() to repository.history() }
            .onSuccess { (balance, history) -> _uiState.update { it.copy(isLoading = false, isRefreshing = false, balance = balance, history = history) } }
            .onFailure { failure -> _uiState.update { it.copy(isLoading = false, isRefreshing = false, error = failure.toUiDataError()) } }
    }
}
