package com.snabbit.prototype.mobile.ui.login

import com.snabbit.prototype.mobile.domain.auth.AuthFailure
import com.snabbit.prototype.mobile.domain.auth.AuthFailureReason
import com.snabbit.prototype.mobile.domain.auth.AuthRepository
import com.snabbit.prototype.mobile.domain.auth.OtpChallenge
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
class LoginViewModelTest {
    @Test
    fun `request normalizes phone and starts resend cooldown`() = runTest {
        val repository = FakeAuthRepository()
        val viewModel = LoginViewModel(repository, StandardTestDispatcher(testScheduler))

        viewModel.updatePhone("98765 43210")
        viewModel.requestOtp()
        runCurrent()

        assertEquals("+919876543210", repository.requestedPhone)
        assertEquals(LoginStep.OTP, viewModel.uiState.value.step)
        assertEquals(30, viewModel.uiState.value.resendSeconds)

        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(25, viewModel.uiState.value.resendSeconds)
    }

    @Test
    fun `invalid phone does not call repository`() = runTest {
        val repository = FakeAuthRepository()
        val viewModel = LoginViewModel(repository, StandardTestDispatcher(testScheduler))

        viewModel.updatePhone("123")
        viewModel.requestOtp()

        assertEquals(LoginError.INVALID_PHONE, viewModel.uiState.value.error)
        assertEquals(null, repository.requestedPhone)
    }

    @Test
    fun `successful verification authenticates session`() = runTest {
        val repository = FakeAuthRepository()
        val viewModel = LoginViewModel(repository, StandardTestDispatcher(testScheduler))
        viewModel.updatePhone("9876543210")
        viewModel.requestOtp()
        runCurrent()
        viewModel.updateOtp("123456")

        viewModel.verifyOtp()
        runCurrent()

        assertEquals("+919876543210", repository.verifiedPhone)
        assertEquals("123456", repository.verifiedOtp)
        assertTrue(viewModel.uiState.value.authenticated)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `offline failure is exposed as recoverable error`() = runTest {
        val repository = FakeAuthRepository(requestFailure = AuthFailure(AuthFailureReason.OFFLINE))
        val viewModel = LoginViewModel(repository, StandardTestDispatcher(testScheduler))
        viewModel.updatePhone("9876543210")

        viewModel.requestOtp()
        runCurrent()

        assertEquals(LoginError.OFFLINE, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `expired otp remains on verification screen`() = runTest {
        val repository = FakeAuthRepository(
            verifyFailure = AuthFailure(AuthFailureReason.INVALID_OR_EXPIRED_OTP),
        )
        val viewModel = LoginViewModel(repository, StandardTestDispatcher(testScheduler))
        viewModel.updatePhone("9876543210")
        viewModel.requestOtp()
        runCurrent()
        viewModel.updateOtp("123456")

        viewModel.verifyOtp()
        runCurrent()

        assertEquals(LoginStep.OTP, viewModel.uiState.value.step)
        assertEquals(LoginError.INVALID_OTP, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.authenticated)
    }
}

private class FakeAuthRepository(
    private val requestFailure: Throwable? = null,
    private val verifyFailure: Throwable? = null,
) : AuthRepository {
    var requestedPhone: String? = null
    var verifiedPhone: String? = null
    var verifiedOtp: String? = null

    override suspend fun requestOtp(phoneNumber: String): OtpChallenge {
        requestFailure?.let { throw it }
        requestedPhone = phoneNumber
        return OtpChallenge(expiresInSeconds = 120)
    }

    override suspend fun verifyOtp(phoneNumber: String, otp: String) {
        verifyFailure?.let { throw it }
        verifiedPhone = phoneNumber
        verifiedOtp = otp
    }
}
