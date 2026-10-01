package com.snabbit.prototype.worker.api

import com.snabbit.prototype.worker.auth.WorkerAccessTokenResult
import com.snabbit.prototype.worker.auth.WorkerAuthenticationService
import com.snabbit.prototype.worker.auth.WorkerOtpRequestResult
import jakarta.validation.Valid
import jakarta.validation.constraints.Pattern
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class WorkerOtpRequest(@field:Pattern(regexp = "^\\+91[6-9][0-9]{9}$") val phoneNumber: String)
data class WorkerOtpVerification(
    @field:Pattern(regexp = "^\\+91[6-9][0-9]{9}$") val phoneNumber: String,
    @field:Pattern(regexp = "^[0-9]{6}$") val otp: String,
)

@RestController
@RequestMapping("/api/v1/worker-auth")
class WorkerAuthController(private val authentication: WorkerAuthenticationService) {
    @PostMapping("/otp/request")
    fun request(@Valid @RequestBody request: WorkerOtpRequest): WorkerOtpRequestResult =
        authentication.requestOtp(request.phoneNumber)

    @PostMapping("/otp/verify")
    fun verify(@Valid @RequestBody request: WorkerOtpVerification): WorkerAccessTokenResult =
        authentication.verifyOtp(request.phoneNumber, request.otp)
}
