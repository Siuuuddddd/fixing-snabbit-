package com.snabbit.prototype.user.dto.request

import com.snabbit.prototype.user.dto.response.AccessTokenResult

import com.snabbit.prototype.user.dto.response.OtpRequestResult

import com.snabbit.prototype.user.auth.OtpAuthenticationService
import jakarta.validation.Valid
import jakarta.validation.constraints.Pattern
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class VerifyOtpRequest(
    @field:Pattern(regexp = "^\\+91[6-9][0-9]{9}$")
    val phoneNumber: String,
    @field:Pattern(regexp = "^[0-9]{6}$")
    val otp: String,
)
