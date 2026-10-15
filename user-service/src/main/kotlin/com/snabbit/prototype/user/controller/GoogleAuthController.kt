package com.snabbit.prototype.user.controller

import com.snabbit.prototype.platform.GoogleIdentityProperties
import com.snabbit.prototype.platform.GoogleIdentityVerifier
import com.snabbit.prototype.user.auth.CustomerGoogleAccountService
import com.snabbit.prototype.user.auth.CustomerAuthRateLimiter
import com.snabbit.prototype.user.auth.CustomerSessionService
import com.snabbit.prototype.user.dto.request.GoogleSignInRequest
import com.snabbit.prototype.user.dto.request.RefreshSessionRequest
import com.snabbit.prototype.user.dto.response.CustomerSessionResponse
import jakarta.validation.Valid
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/auth")
class GoogleAuthController(
    private val verifier: GoogleIdentityVerifier,
    private val google: GoogleIdentityProperties,
    private val accounts: CustomerGoogleAccountService,
    private val sessions: CustomerSessionService,
    private val rateLimiter: CustomerAuthRateLimiter,
) {
    @PostMapping("/google")
    fun signIn(@Valid @RequestBody request: GoogleSignInRequest): CustomerSessionResponse {
        val identity = verifier.verify(request.idToken, google.customerAudiences(), request.nonce)
        rateLimiter.check("sign-in", identity.subject)
        val account = accounts.resolve(identity)
        return sessions.create(account.profile, request.deviceId, request.deviceName, account.newUser)
    }

    @PostMapping("/refresh")
    fun refresh(@Valid @RequestBody request: RefreshSessionRequest): CustomerSessionResponse =
        sessions.refresh(request.refreshToken)

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun logout(
        @RequestHeader("X-Authenticated-User-Id") userId: UUID,
        @RequestHeader("X-Authenticated-Session-Id") sessionId: UUID,
    ) = sessions.logout(userId, sessionId)

    @PostMapping("/logout-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun logoutAll(@RequestHeader("X-Authenticated-User-Id") userId: UUID) = sessions.logoutAll(userId)
}
