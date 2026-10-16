package com.snabbit.prototype.mobile.domain.auth

class SignInWithGoogleUseCase(
    private val tokenProvider: GoogleIdTokenProvider,
    private val repository: AuthRepository,
) {
    suspend operator fun invoke(
        device: AuthDevice = AuthDevice(),
        rememberMe: Boolean = true,
    ): GoogleSignInResult = repository.signInWithGoogle(tokenProvider.requestIdToken(), device, rememberMe)
}

class RequestPhoneOtpUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(phoneNumber: String): Long = repository.requestPhoneOtp(phoneNumber)
}

class VerifyPhoneOtpUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(
        phoneNumber: String,
        code: String,
        rememberMe: Boolean,
        name: String? = null,
        device: AuthDevice = AuthDevice(),
    ): AuthSession = repository.verifyPhoneOtp(phoneNumber, code, rememberMe, name, device)
}

class SelectWorkerProfileUseCase(
    private val tokenProvider: GoogleIdTokenProvider,
    private val repository: AuthRepository,
) {
    suspend operator fun invoke(selectionToken: String, workerId: String, device: AuthDevice = AuthDevice()): AuthSession =
        repository.selectWorkerProfile(tokenProvider.requestIdToken(), selectionToken, workerId, device)
}

class SwitchWorkerProfileUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(workerId: String): AuthSession = repository.switchWorkerProfile(workerId)
}

class RefreshSessionUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(): AuthSession = repository.refreshSession()
}

class SignOutUseCase(
    private val repository: AuthRepository,
    private val tokenProvider: GoogleIdTokenProvider,
) {
    suspend operator fun invoke() {
        try {
            repository.signOut(allDevices = false)
        } finally {
            tokenProvider.clearCredentialState()
        }
    }
}

class SignOutAllUseCase(
    private val repository: AuthRepository,
    private val tokenProvider: GoogleIdTokenProvider,
) {
    suspend operator fun invoke() {
        try {
            repository.signOut(allDevices = true)
        } finally {
            tokenProvider.clearCredentialState()
        }
    }
}

class ObserveSessionUseCase(private val store: SessionStore) {
    operator fun invoke() = store.session
}
