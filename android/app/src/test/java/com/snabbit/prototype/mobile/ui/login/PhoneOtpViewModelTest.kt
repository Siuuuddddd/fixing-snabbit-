package com.snabbit.prototype.mobile.ui.login

import com.snabbit.prototype.mobile.domain.auth.AuthDevice
import com.snabbit.prototype.mobile.domain.auth.AuthFailure
import com.snabbit.prototype.mobile.domain.auth.AuthFailureReason
import com.snabbit.prototype.mobile.domain.auth.AuthRepository
import com.snabbit.prototype.mobile.domain.auth.AuthSession
import com.snabbit.prototype.mobile.domain.auth.RequestPhoneOtpUseCase
import com.snabbit.prototype.mobile.domain.auth.SessionUser
import com.snabbit.prototype.mobile.domain.auth.VerifyPhoneOtpUseCase
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PhoneOtpViewModelTest {
    private val phone = "+91" + "9" + "0".repeat(8) + "1"

    @Test
    fun `six digits auto submit and forward remember me`() = runTest {
        val repository = RecordingPhoneRepository()
        val viewModel = viewModel(repository, rememberMe = false)
        val event = async(start = CoroutineStart.UNDISPATCHED) { viewModel.events.first() }

        viewModel.updateCode("12a3456")
        runCurrent()

        assertEquals("123456", repository.verifiedCode)
        assertEquals(false, repository.verifiedRememberMe)
        assertEquals(PhoneOtpUiEvent.Authenticated, event.await())
        assertEquals("", viewModel.uiState.value.code)
    }

    @Test
    fun `resend requests a fresh code after countdown reaches zero`() = runTest {
        val repository = RecordingPhoneRepository()
        val viewModel = viewModel(repository, rememberMe = true)

        viewModel.resend()
        runCurrent()

        assertEquals(phone, repository.requestedPhone)
        assertEquals(30, viewModel.uiState.value.resendSeconds)
    }

    @Test
    fun `invalid code clears the field and shows a friendly state`() = runTest {
        val repository = RecordingPhoneRepository().apply {
            verifyFailure = AuthFailure(AuthFailureReason.INVALID_OR_EXPIRED_CODE)
        }
        val viewModel = viewModel(repository, rememberMe = true)

        viewModel.updateCode("123456")
        runCurrent()

        assertEquals("", viewModel.uiState.value.code)
        assertEquals(PhoneOtpError.INVALID_OR_EXPIRED, viewModel.uiState.value.error)
    }

    @Test
    fun `rate limit applies the backend retry countdown`() = runTest {
        val repository = RecordingPhoneRepository().apply {
            verifyFailure = AuthFailure(AuthFailureReason.RATE_LIMITED, retryAfterSeconds = 42)
        }
        val viewModel = viewModel(repository, rememberMe = true)

        viewModel.updateCode("123456")
        runCurrent()

        assertEquals(PhoneOtpError.RATE_LIMITED, viewModel.uiState.value.error)
        assertEquals(42, viewModel.uiState.value.resendSeconds)
    }

    private fun kotlinx.coroutines.test.TestScope.viewModel(
        repository: RecordingPhoneRepository,
        rememberMe: Boolean,
    ) = PhoneOtpViewModel(
        phone,
        rememberMe,
        initialResendSeconds = 0,
        VerifyPhoneOtpUseCase(repository),
        RequestPhoneOtpUseCase(repository),
        StandardTestDispatcher(testScheduler),
    )
}

private class RecordingPhoneRepository : AuthRepository {
    var requestedPhone: String? = null
    var verifiedCode: String? = null
    var verifiedRememberMe: Boolean? = null
    var verifyFailure: AuthFailure? = null

    override suspend fun requestPhoneOtp(phoneNumber: String): Long {
        requestedPhone = phoneNumber
        return 30
    }

    override suspend fun verifyPhoneOtp(
        phoneNumber: String,
        code: String,
        rememberMe: Boolean,
        name: String?,
        device: AuthDevice,
    ): AuthSession {
        verifiedCode = code
        verifiedRememberMe = rememberMe
        verifyFailure?.let { throw it }
        return AuthSession(
            "access", "Bearer", 1, "refresh",
            SessionUser("user", "Test User", null, null, false, "CUSTOMER", phoneNumber = phoneNumber),
        )
    }
}
