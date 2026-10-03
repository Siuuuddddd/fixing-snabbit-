package com.snabbit.prototype.user.controller

import com.snabbit.prototype.user.dto.response.CustomerContactSnapshot
import java.util.UUID
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/internal/v1/users")
class InternalCustomerContactController(private val service: CustomerContactService) {
    @GetMapping("/{userId}/contact")
    fun contact(@PathVariable userId: UUID): CustomerContactSnapshot = service.contact(userId)
}
