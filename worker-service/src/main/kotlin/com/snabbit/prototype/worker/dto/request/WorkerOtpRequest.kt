package com.snabbit.prototype.worker.dto.request

import com.snabbit.prototype.worker.dto.response.WorkerOtpRequestResult

import com.snabbit.prototype.worker.dto.response.WorkerAccessTokenResult

import com.snabbit.prototype.worker.auth.WorkerAuthenticationService
import jakarta.validation.Valid
import jakarta.validation.constraints.Pattern
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class WorkerOtpRequest(@field:Pattern(regexp = "^\\+91[6-9][0-9]{9}$") val phoneNumber: String)
