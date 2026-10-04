package com.snabbit.prototype.mobile.ui.plus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snabbit.prototype.mobile.domain.payment.CheckoutPurpose
import com.snabbit.prototype.mobile.domain.payment.CheckoutRequest
import com.snabbit.prototype.mobile.domain.payment.CheckoutResult
import com.snabbit.prototype.mobile.domain.payment.PaymentCheckoutCoordinator
import com.snabbit.prototype.mobile.domain.plus.PlusPlan
import com.snabbit.prototype.mobile.domain.plus.PlusRepository
import com.snabbit.prototype.mobile.domain.plus.PlusStatus
import com.snabbit.prototype.mobile.ui.common.UiDataError
import com.snabbit.prototype.mobile.ui.common.toUiDataError
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class PlusPurchasePhase { IDLE, CREATING_ORDER, CHECKOUT, VERIFYING, CONFIRMED, FAILED }

data class PlusUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val status: PlusStatus? = null,
    val plans: List<PlusPlan> = emptyList(),
    val phase: PlusPurchasePhase = PlusPurchasePhase.IDLE,
    val message: String? = null,
    val error: UiDataError? = null,
)

class PlusViewModel(
    private val repository: PlusRepository,
    private val checkout: PaymentCheckoutCoordinator,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main,
    private val verificationDelayMillis: Long = 3_000,
    private val verificationAttempts: Int = 20,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PlusUiState(plans = repository.plans()))
    val uiState: StateFlow<PlusUiState> = _uiState.asStateFlow()
    private var activeRequestId: String? = null
    private var baselineExpiry: String? = null
    private var idempotencyKey = UUID.randomUUID().toString()

    init {
        load(false)
        viewModelScope.launch(dispatcher) {
            checkout.results.collect { result ->
                if (result.requestId != activeRequestId) return@collect
                when (result) {
                    is CheckoutResult.ClientSuccess -> verifyBackend()
                    is CheckoutResult.ClientFailure -> _uiState.update {
                        val message = result.description?.takeIf(String::isNotBlank)
                            ?.let { detail -> "$detail Membership was not activated." }
                            ?: "Payment was not completed. Membership was not activated."
                        it.copy(phase = PlusPurchasePhase.FAILED, message = message)
                    }
                }
            }
        }
    }

    fun refresh() = load(true)
    fun retry() = load(false)
    fun join(plan: PlusPlan) {
        if (_uiState.value.phase in setOf(PlusPurchasePhase.CREATING_ORDER, PlusPurchasePhase.CHECKOUT, PlusPurchasePhase.VERIFYING)) return
        viewModelScope.launch(dispatcher) {
            _uiState.update { it.copy(phase = PlusPurchasePhase.CREATING_ORDER, message = "Creating a secure membership order…", error = null) }
            runCatching { repository.purchase(plan.code, idempotencyKey) }
                .onSuccess { purchase ->
                    val providerOrder = purchase.providerOrderId
                    if (providerOrder.isNullOrBlank()) {
                        _uiState.update { it.copy(phase = PlusPurchasePhase.FAILED, message = "The backend did not return a payment order. Membership was not activated.") }
                        return@onSuccess
                    }
                    activeRequestId = UUID.randomUUID().toString()
                    baselineExpiry = _uiState.value.status?.expiresAt
                    _uiState.update { it.copy(phase = PlusPurchasePhase.CHECKOUT, message = "Complete payment in Razorpay.") }
                    checkout.request(CheckoutRequest(activeRequestId!!, CheckoutPurpose.PLUS_MEMBERSHIP, providerOrder, purchase.amountMinor, "Snabbit Plus membership"))
                }
                .onFailure { failure -> _uiState.update { it.copy(phase = PlusPurchasePhase.FAILED, message = null, error = failure.toUiDataError()) } }
        }
    }

    fun checkAgain() {
        if (activeRequestId != null) viewModelScope.launch(dispatcher) { verifyBackend() }
    }

    private suspend fun verifyBackend() {
        _uiState.update { it.copy(phase = PlusPurchasePhase.VERIFYING, message = "Payment submitted. Waiting for backend membership confirmation.") }
        repeat(verificationAttempts) { attempt ->
            val status = runCatching { repository.status() }.getOrNull()
            if (status != null && status.active && (baselineExpiry == null || status.expiresAt != baselineExpiry)) {
                _uiState.update { it.copy(status = status, phase = PlusPurchasePhase.CONFIRMED, message = "Backend confirmed your Snabbit Plus membership.") }
                idempotencyKey = UUID.randomUUID().toString()
                return
            }
            if (attempt < verificationAttempts - 1) delay(verificationDelayMillis)
        }
        _uiState.update { it.copy(phase = PlusPurchasePhase.VERIFYING, message = "Still awaiting backend confirmation. Membership remains unchanged.") }
    }

    private fun load(refreshing: Boolean) = viewModelScope.launch(dispatcher) {
        _uiState.update { it.copy(isLoading = !refreshing && it.status == null, isRefreshing = refreshing, error = null) }
        runCatching { repository.status() }
            .onSuccess { status -> _uiState.update { it.copy(isLoading = false, isRefreshing = false, status = status) } }
            .onFailure { failure -> _uiState.update { it.copy(isLoading = false, isRefreshing = false, error = failure.toUiDataError()) } }
    }
}
