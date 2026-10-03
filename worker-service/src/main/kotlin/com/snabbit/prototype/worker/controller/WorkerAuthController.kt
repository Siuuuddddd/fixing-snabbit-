package com.snabbit.prototype.worker.controller

import com.snabbit.prototype.worker.dto.response.WorkerOtpRequestResult

import com.snabbit.prototype.worker.dto.response.WorkerAccessTokenResult

import com.snabbit.prototype.worker.dto.request.WorkerOtpVerification

import com.snabbit.prototype.worker.dto.request.WorkerOtpRequest

import com.snabbit.prototype.worker.auth.WorkerAuthenticationService
import jakarta.validation.Valid
import jakarta.validation.constraints.Pattern
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

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
