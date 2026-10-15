package com.snabbit.prototype.worker.controller

import com.snabbit.prototype.platform.GoogleIdentityProperties
import com.snabbit.prototype.platform.GoogleIdentityVerifier
import com.snabbit.prototype.worker.auth.WorkerAuthProperties
import com.snabbit.prototype.worker.auth.WorkerAuthRateLimiter
import com.snabbit.prototype.worker.auth.WorkerGoogleAccountResult
import com.snabbit.prototype.worker.auth.WorkerGoogleAccountService
import com.snabbit.prototype.worker.auth.WorkerSessionService
import com.snabbit.prototype.worker.dto.request.WorkerGoogleSignInRequest
import com.snabbit.prototype.worker.dto.request.WorkerRefreshSessionRequest
import com.snabbit.prototype.worker.dto.request.WorkerSelectProfileRequest
import com.snabbit.prototype.worker.dto.request.WorkerSwitchProfileRequest
import com.snabbit.prototype.worker.dto.response.WorkerPickerResponse
import com.snabbit.prototype.worker.dto.response.WorkerSessionResponse
import com.snabbit.prototype.worker.dto.response.WorkerSignInResponse
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
@RequestMapping("/api/v1/worker-auth")
class GoogleWorkerAuthController(
    private val verifier: GoogleIdentityVerifier,
    private val google: GoogleIdentityProperties,
    private val accounts: WorkerGoogleAccountService,
    private val sessions: WorkerSessionService,
    private val auth: WorkerAuthProperties,
    private val rateLimiter: WorkerAuthRateLimiter,
) {
    @PostMapping("/google")
    fun signIn(@Valid @RequestBody request: WorkerGoogleSignInRequest): WorkerSignInResponse {
        val identity = verifier.verify(request.idToken, google.workerAudiences(), request.nonce)
        rateLimiter.check("sign-in", identity.subject)
        return when (val result = accounts.resolve(identity)) {
            is WorkerGoogleAccountResult.Linked ->
                sessions.create(result.profile, request.deviceId, request.deviceName, false)
            is WorkerGoogleAccountResult.Picker -> WorkerPickerResponse(
                result.selectionToken, auth.sharedSelectionTokenTtl.seconds, result.workers,
            )
        }
    }

    @PostMapping("/select-profile")
    fun select(@Valid @RequestBody request: WorkerSelectProfileRequest): WorkerSessionResponse {
        val identity = verifier.verify(request.idToken, google.workerAudiences(), request.nonce)
        rateLimiter.check("select-profile", identity.subject)
        val worker = accounts.select(identity, request.selectionToken, request.workerId)
        return sessions.create(worker, request.deviceId, request.deviceName, true)
    }

    @PostMapping("/refresh")
    fun refresh(@Valid @RequestBody request: WorkerRefreshSessionRequest): WorkerSessionResponse =
        sessions.refresh(request.refreshToken)

    @PostMapping("/switch-profile")
    fun switchProfile(
        @RequestHeader("X-Authenticated-Worker-Id") workerId: UUID,
        @RequestHeader("X-Authenticated-Session-Id") sessionId: UUID,
        @RequestBody request: WorkerSwitchProfileRequest,
    ): WorkerSessionResponse = sessions.switchProfile(workerId, sessionId, request.workerId)

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun logout(
        @RequestHeader("X-Authenticated-Worker-Id") workerId: UUID,
        @RequestHeader("X-Authenticated-Session-Id") sessionId: UUID,
    ) = sessions.logout(workerId, sessionId)

    @PostMapping("/logout-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun logoutAll(@RequestHeader("X-Authenticated-Worker-Id") workerId: UUID) = sessions.logoutAll(workerId)
}
