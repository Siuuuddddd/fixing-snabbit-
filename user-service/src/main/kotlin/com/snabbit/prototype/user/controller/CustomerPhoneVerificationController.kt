package com.snabbit.prototype.user.controller

import com.snabbit.prototype.user.dto.request.SendPhoneVerificationRequest
import com.snabbit.prototype.user.dto.request.VerifyPhoneRequest
import com.snabbit.prototype.user.dto.response.CustomerContactSnapshot
import com.snabbit.prototype.user.dto.response.PhoneVerificationAccepted
import com.snabbit.prototype.user.service.CustomerPhoneVerificationService
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import java.util.UUID
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/users/{userId}/phone-verification")
class CustomerPhoneVerificationController(private val verification: CustomerPhoneVerificationService) {
    @PostMapping("/send")
    fun send(
        @PathVariable userId: UUID,
        @RequestHeader("X-Authenticated-User-Id") authenticatedUserId: UUID,
        @Valid @RequestBody body: SendPhoneVerificationRequest,
        request: HttpServletRequest,
    ): PhoneVerificationAccepted = verification.send(userId, authenticatedUserId, body.phoneNumber, clientIp(request))

    @PostMapping("/verify")
    fun verify(
        @PathVariable userId: UUID,
        @RequestHeader("X-Authenticated-User-Id") authenticatedUserId: UUID,
        @Valid @RequestBody body: VerifyPhoneRequest,
        request: HttpServletRequest,
    ): CustomerContactSnapshot = verification.verify(
        userId,
        authenticatedUserId,
        body.phoneNumber,
        body.code,
        clientIp(request),
    )

    private fun clientIp(request: HttpServletRequest): String =
        request.getHeader("X-Forwarded-For")?.substringBefore(',')?.trim()?.takeIf(String::isNotBlank)
            ?: request.remoteAddr.orEmpty()
}
