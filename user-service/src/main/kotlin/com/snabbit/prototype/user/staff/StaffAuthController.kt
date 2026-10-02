package com.snabbit.prototype.user.staff

import com.snabbit.prototype.user.auth.AccessTokenResult
import com.snabbit.prototype.user.auth.OtpRequestResult
import jakarta.validation.Valid
import jakarta.validation.constraints.Pattern
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class StaffOtpRequest(@field:Pattern(regexp = "^\\+91[6-9][0-9]{9}$") val phoneNumber: String)
data class StaffOtpVerification(
    @field:Pattern(regexp = "^\\+91[6-9][0-9]{9}$") val phoneNumber: String,
    @field:Pattern(regexp = "^[0-9]{6}$") val otp: String,
)

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
