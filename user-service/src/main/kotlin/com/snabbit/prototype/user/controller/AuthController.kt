package com.snabbit.prototype.user.controller

import com.snabbit.prototype.user.auth.OtpAuthenticationService
import com.snabbit.prototype.user.dto.request.RequestOtpRequest
import com.snabbit.prototype.user.dto.request.VerifyOtpRequest
import com.snabbit.prototype.user.dto.response.AccessTokenResult
import com.snabbit.prototype.user.dto.response.OtpRequestResult
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(private val authentication: OtpAuthenticationService) {
    @PostMapping("/otp/request")
    fun request(@Valid @RequestBody request: RequestOtpRequest): OtpRequestResult =
        authentication.requestOtp(request.phoneNumber)

    @PostMapping("/otp/verify")
    fun verify(@Valid @RequestBody request: VerifyOtpRequest): AccessTokenResult =
        authentication.verifyOtp(request.phoneNumber, request.otp)
}
