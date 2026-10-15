package com.snabbit.prototype.mobile.domain.auth

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class EmailAuthUseCasesTest {
    @Test fun `all email authentication use cases delegate exact values`() = runTest {
        val repository = RecordingEmailAuthRepository()
        RegisterUseCase(repository)("Test Customer", "person@example.invalid", credential())
        VerifyEmailUseCase(repository)("person@example.invalid", code())
        ResendVerificationUseCase(repository)("person@example.invalid")
        LoginWithPasswordUseCase(repository)("person@example.invalid", credential())
        ForgotPasswordUseCase(repository)("person@example.invalid")
        ResetPasswordUseCase(repository)("person@example.invalid", code(), credential())
        ChangePasswordUseCase(repository)(credential(), replacementCredential())

        assertEquals(listOf("register", "verify", "resend", "login", "forgot", "reset", "change"), repository.calls)
    }

    @Test fun `use cases preserve typed backend failures`() = runTest {
        AuthFailureReason.entries.filterNot {
            it in setOf(
                AuthFailureReason.CANCELLED, AuthFailureReason.NOT_CONFIGURED,
                AuthFailureReason.NO_GOOGLE_ACCOUNT, AuthFailureReason.PLAY_SERVICES_UNAVAILABLE,
                AuthFailureReason.GOOGLE_CONFIGURATION, AuthFailureReason.INVALID_GOOGLE_TOKEN,
                AuthFailureReason.WORKER_NOT_REGISTERED, AuthFailureReason.INVALID_SELECTION_TOKEN,
                AuthFailureReason.ACCOUNT_LINK_CONFLICT, AuthFailureReason.REFRESH_IN_PROGRESS,
                AuthFailureReason.SHARED_WORKER_SESSION_REQUIRED, AuthFailureReason.PHONE_REQUIRED,
                AuthFailureReason.WORKER_PROFILE_REQUIRED,
            )
        }.forEach { reason ->
            val repository = RecordingEmailAuthRepository(AuthFailure(reason))
            val failure = runCatching { LoginWithPasswordUseCase(repository)("person@example.invalid", credential()) }.exceptionOrNull()
            assertEquals(reason, (failure as AuthFailure).reason)
        }
    }
}

private class RecordingEmailAuthRepository(private val failure: Throwable? = null) : EmailAuthRepository {
    val calls = mutableListOf<String>()
    private fun record(value: String) { failure?.let { throw it }; calls += value }
    override suspend fun register(name: String, email: String, password: String, device: AuthDevice) = record("register")
    override suspend fun verifyEmail(email: String, code: String, device: AuthDevice): AuthSession { record("verify"); return session() }
    override suspend fun resendVerification(email: String) = record("resend")
    override suspend fun login(email: String, password: String, device: AuthDevice): AuthSession { record("login"); return session() }
    override suspend fun forgotPassword(email: String) = record("forgot")
    override suspend fun resetPassword(email: String, code: String, newPassword: String) = record("reset")
    override suspend fun changePassword(currentPassword: String, newPassword: String): AuthSession { record("change"); return session() }
    override suspend fun currentCustomer(): SessionUser = session().user
}

private fun session() = AuthSession("access", "Bearer", 1L, "refresh", SessionUser("id", "Test", "person@example.invalid", null, false, "CUSTOMER"))
private fun credential() = "Synthetic!Credential-2042"
private fun replacementCredential() = "Synthetic!Replacement-2043"
private fun code() = (1..6).joinToString("") { it.toString() }

