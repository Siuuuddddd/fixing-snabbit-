package com.snabbit.prototype.user.controller

import com.snabbit.prototype.user.auth.CustomerEmailAuthService
import com.snabbit.prototype.user.auth.CustomerSessionService
import com.snabbit.prototype.user.dto.request.ChangePasswordRequest
import com.snabbit.prototype.user.dto.request.EmailLoginRequest
import com.snabbit.prototype.user.dto.request.EmailOnlyRequest
import com.snabbit.prototype.user.dto.request.EmailRegisterRequest
import com.snabbit.prototype.user.dto.request.ResetPasswordRequest
import com.snabbit.prototype.user.dto.request.VerifyEmailRequest
import com.snabbit.prototype.user.dto.response.AcceptedAuthRequest
import com.snabbit.prototype.user.dto.response.CustomerSessionResponse
import com.snabbit.prototype.user.dto.response.CustomerSessionUser
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/auth")
class EmailAuthController(
    private val emailAuth: CustomerEmailAuthService,
    private val sessions: CustomerSessionService,
) {
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun register(@Valid @RequestBody request: EmailRegisterRequest, servlet: HttpServletRequest): AcceptedAuthRequest {
        emailAuth.register(request, clientIp(servlet))
        return AcceptedAuthRequest()
    }

    @PostMapping("/verify-email")
    fun verify(@Valid @RequestBody request: VerifyEmailRequest, servlet: HttpServletRequest): CustomerSessionResponse =
        emailAuth.verify(request, clientIp(servlet))

    @PostMapping("/resend-verification")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun resend(@Valid @RequestBody request: EmailOnlyRequest, servlet: HttpServletRequest): AcceptedAuthRequest {
        emailAuth.resend(request.email, clientIp(servlet))
        return AcceptedAuthRequest()
    }

    @PostMapping("/login")
    fun login(@Valid @RequestBody request: EmailLoginRequest, servlet: HttpServletRequest): CustomerSessionResponse =
        emailAuth.login(request, clientIp(servlet))

    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun forgot(@Valid @RequestBody request: EmailOnlyRequest, servlet: HttpServletRequest): AcceptedAuthRequest {
        emailAuth.forgotPassword(request.email, clientIp(servlet))
        return AcceptedAuthRequest()
    }

    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun reset(@Valid @RequestBody request: ResetPasswordRequest, servlet: HttpServletRequest) =
        emailAuth.resetPassword(request, clientIp(servlet))

    @PostMapping("/change-password")
    fun change(
        @RequestHeader("X-Authenticated-User-Id") userId: UUID,
        @RequestHeader("X-Authenticated-Session-Id") sessionId: UUID,
        @Valid @RequestBody request: ChangePasswordRequest,
    ): CustomerSessionResponse = emailAuth.changePassword(userId, sessionId, request)

    @GetMapping("/me")
    fun me(@RequestHeader("X-Authenticated-User-Id") userId: UUID): CustomerSessionUser = sessions.currentUser(userId)

    private fun clientIp(request: HttpServletRequest): String = request.remoteAddr ?: "unknown"
}
