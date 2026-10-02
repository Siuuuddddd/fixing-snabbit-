package com.snabbit.prototype.user.controller

import com.snabbit.prototype.user.auth.AccessTokenResult
import com.snabbit.prototype.user.auth.OtpAuthenticationService
import com.snabbit.prototype.user.auth.OtpRequestResult
import jakarta.validation.Valid
import jakarta.validation.constraints.Pattern
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class RequestOtpRequest(
    @field:Pattern(regexp = "^\\+91[6-9][0-9]{9}$")
    val phoneNumber: String,
)

data class VerifyOtpRequest(
    @field:Pattern(regexp = "^\\+91[6-9][0-9]{9}$")
    val phoneNumber: String,
    @field:Pattern(regexp = "^[0-9]{6}$")
    val otp: String,
)

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
