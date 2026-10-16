package com.snabbit.prototype.booking.controller

import com.snabbit.prototype.booking.dto.request.VerifyArrivalOtpRequest
import com.snabbit.prototype.booking.dto.response.ArrivalOtpStatusSnapshot
import com.snabbit.prototype.booking.service.ArrivalOtpCommandService
import jakarta.validation.Valid
import java.util.UUID
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/bookings/{bookingId}/visits/{visitIndex}/arrival-otp")
class CustomerArrivalOtpController(private val arrivalOtp: ArrivalOtpCommandService) {
    @PostMapping("/verify")
    fun verify(
        @PathVariable bookingId: UUID,
        @PathVariable visitIndex: Int,
        @RequestHeader("X-Authenticated-User-Id") customerId: UUID,
        @Valid @RequestBody request: VerifyArrivalOtpRequest,
    ): ArrivalOtpStatusSnapshot = arrivalOtp.verifyForCustomer(bookingId, visitIndex, customerId, request.code)

    @PostMapping("/resend")
    fun resend(
        @PathVariable bookingId: UUID,
        @PathVariable visitIndex: Int,
        @RequestHeader("X-Authenticated-User-Id") customerId: UUID,
    ): ArrivalOtpStatusSnapshot = arrivalOtp.resendForCustomer(bookingId, visitIndex, customerId)
}
