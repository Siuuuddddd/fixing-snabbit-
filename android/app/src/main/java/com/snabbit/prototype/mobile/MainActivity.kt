package com.snabbit.prototype.mobile

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.razorpay.Checkout
import com.razorpay.PaymentData
import com.razorpay.PaymentResultWithDataListener
import com.snabbit.prototype.mobile.domain.payment.CheckoutRequest
import com.snabbit.prototype.mobile.domain.payment.CheckoutResult
import com.snabbit.prototype.mobile.domain.payment.CheckoutPurpose
import com.snabbit.prototype.mobile.domain.payment.PaymentCheckoutCoordinator
import com.snabbit.prototype.mobile.navigation.SnabbitApp
import com.snabbit.prototype.mobile.navigation.AppRole
import com.snabbit.prototype.mobile.navigation.PushNavigationCoordinator
import com.snabbit.prototype.mobile.ui.theme.SnabbitTheme
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity(), PaymentResultWithDataListener {
    private val checkoutCoordinator: PaymentCheckoutCoordinator by inject()
    private val pushNavigation: PushNavigationCoordinator by inject()
    private var activeCheckout: CheckoutRequest? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activeCheckout = savedInstanceState?.let(::restoreCheckout)
        handlePushIntent(intent)
        enableEdgeToEdge()
        Checkout.preload(applicationContext)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                checkoutCoordinator.requests.collect(::openCheckout)
            }
        }
        setContent {
            SnabbitTheme {
                SnabbitApp()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handlePushIntent(intent)
    }

    private fun handlePushIntent(intent: Intent) {
        val data = listOf("type", "bookingId", "jobId", "walletId", "visitIndex")
            .mapNotNull { key -> intent.getStringExtra(key)?.let { value -> key to value } }
            .toMap()
        if (data.isNotEmpty()) pushNavigation.open(data, AppRole.fromBuild())
    }

    override fun onSaveInstanceState(outState: Bundle) {
        activeCheckout?.let { request ->
            outState.putString("checkout.requestId", request.requestId)
            outState.putString("checkout.purpose", request.purpose.name)
            outState.putString("checkout.providerOrderId", request.providerOrderId)
            outState.putLong("checkout.amountMinor", request.amountMinor)
            outState.putString("checkout.description", request.description)
        }
        super.onSaveInstanceState(outState)
    }

    private fun restoreCheckout(state: Bundle): CheckoutRequest? {
        val requestId = state.getString("checkout.requestId") ?: return null
        val purpose = state.getString("checkout.purpose")?.let { runCatching { CheckoutPurpose.valueOf(it) }.getOrNull() } ?: return null
        val providerOrderId = state.getString("checkout.providerOrderId") ?: return null
        val description = state.getString("checkout.description") ?: return null
        return CheckoutRequest(requestId, purpose, providerOrderId, state.getLong("checkout.amountMinor"), description)
    }

    private fun openCheckout(request: CheckoutRequest) {
        if (activeCheckout != null) {
            checkoutCoordinator.report(CheckoutResult.ClientFailure(request.requestId, -2, "Another checkout is already open."))
            return
        }
        if (BuildConfig.RAZORPAY_KEY_ID.isBlank()) {
            checkoutCoordinator.report(CheckoutResult.ClientFailure(request.requestId, -1, "Configure RAZORPAY_KEY_ID for test checkout."))
            return
        }
        activeCheckout = request
        runCatching {
            Checkout().apply { setKeyID(BuildConfig.RAZORPAY_KEY_ID) }.open(
                this,
                JSONObject().apply {
                    put("name", "Snabbit")
                    put("description", request.description)
                    put("order_id", request.providerOrderId)
                    put("currency", "INR")
                    put("amount", request.amountMinor)
                    put("theme", JSONObject().put("color", "#E83E8C"))
                },
            )
        }.onFailure {
            activeCheckout = null
            checkoutCoordinator.report(CheckoutResult.ClientFailure(request.requestId, -1, it.message))
        }
    }

    override fun onPaymentSuccess(paymentId: String?, paymentData: PaymentData?) {
        val request = activeCheckout ?: return
        activeCheckout = null
        // This callback is only a signal to verify; the backend remains the payment authority.
        checkoutCoordinator.report(CheckoutResult.ClientSuccess(request.requestId))
    }

    override fun onPaymentError(code: Int, response: String?, paymentData: PaymentData?) {
        val request = activeCheckout ?: return
        activeCheckout = null
        checkoutCoordinator.report(CheckoutResult.ClientFailure(request.requestId, code, response))
    }
}
