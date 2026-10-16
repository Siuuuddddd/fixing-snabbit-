package com.snabbit.prototype.user.controller

import com.snabbit.prototype.user.auth.CustomerPhoneAuthService
import com.snabbit.prototype.user.dto.request.PhoneOtpRequest
import com.snabbit.prototype.user.dto.request.PhoneOtpVerifyRequest
import com.snabbit.prototype.user.dto.response.CustomerSessionResponse
import com.snabbit.prototype.user.dto.response.PhoneOtpAccepted
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/auth/otp")
class PhoneAuthController(private val phoneAuth: CustomerPhoneAuthService) {
    @PostMapping("/request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun request(@Valid @RequestBody body: PhoneOtpRequest, servlet: HttpServletRequest): PhoneOtpAccepted =
        PhoneOtpAccepted(retryAfterSeconds = phoneAuth.request(body.phoneNumber, clientIp(servlet)))

    @PostMapping("/verify")
    fun verify(
        @Valid @RequestBody body: PhoneOtpVerifyRequest,
        servlet: HttpServletRequest,
    ): CustomerSessionResponse = phoneAuth.verify(body, clientIp(servlet))

    private fun clientIp(request: HttpServletRequest): String = request.remoteAddr ?: "unknown"
}
