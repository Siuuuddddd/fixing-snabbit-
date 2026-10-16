package com.snabbit.prototype.mobile.domain.auth

class RegisterUseCase(private val repository: EmailAuthRepository) {
    suspend operator fun invoke(name: String, email: String, password: String, device: AuthDevice = AuthDevice()) =
        repository.register(name, email, password, device)
}

class VerifyEmailUseCase(private val repository: EmailAuthRepository) {
    suspend operator fun invoke(email: String, code: String, rememberMe: Boolean = true, device: AuthDevice = AuthDevice()) =
        repository.verifyEmail(email, code, rememberMe, device)
}

class ResendVerificationUseCase(private val repository: EmailAuthRepository) {
    suspend operator fun invoke(email: String) = repository.resendVerification(email)
}

class LoginWithPasswordUseCase(private val repository: EmailAuthRepository) {
    suspend operator fun invoke(email: String, password: String, rememberMe: Boolean = true, device: AuthDevice = AuthDevice()) =
        repository.login(email, password, rememberMe, device)
}

class ForgotPasswordUseCase(private val repository: EmailAuthRepository) {
    suspend operator fun invoke(email: String) = repository.forgotPassword(email)
}

class ResetPasswordUseCase(private val repository: EmailAuthRepository) {
    suspend operator fun invoke(email: String, code: String, newPassword: String) =
        repository.resetPassword(email, code, newPassword)
}

class ChangePasswordUseCase(private val repository: EmailAuthRepository) {
    suspend operator fun invoke(currentPassword: String, newPassword: String) =
        repository.changePassword(currentPassword, newPassword)
}

