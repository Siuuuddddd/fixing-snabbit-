package com.snabbit.prototype.user.controller

import com.snabbit.prototype.user.config.DemoCustomerDevSeed
import java.util.UUID
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@Profile("local", "dev")
@ConditionalOnProperty(prefix = "dev-seed", name = ["enabled"], havingValue = "true")
@RequestMapping("/internal/v1/users/{userId}/dev-seed")
class InternalDevSeedController(private val seed: DemoCustomerDevSeed) {
    @PostMapping("/apply")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun apply(@PathVariable userId: UUID) = seed.ensureFor(userId)
}
