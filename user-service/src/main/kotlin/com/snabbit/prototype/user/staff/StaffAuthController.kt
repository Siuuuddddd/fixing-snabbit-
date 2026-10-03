package com.snabbit.prototype.user.staff

import com.snabbit.prototype.user.dto.request.StaffOtpRequest
import com.snabbit.prototype.user.dto.request.StaffOtpVerification
import com.snabbit.prototype.user.dto.response.AccessTokenResult
import com.snabbit.prototype.user.dto.response.OtpRequestResult
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/staff-auth")
class StaffAuthController(private val authentication: StaffAuthenticationService) {
    @PostMapping("/otp/request")
    fun request(@Valid @RequestBody request: StaffOtpRequest): OtpRequestResult =
        authentication.requestOtp(request.phoneNumber)

    @PostMapping("/otp/verify")
    fun verify(@Valid @RequestBody request: StaffOtpVerification): AccessTokenResult =
        authentication.verifyOtp(request.phoneNumber, request.otp)
}
