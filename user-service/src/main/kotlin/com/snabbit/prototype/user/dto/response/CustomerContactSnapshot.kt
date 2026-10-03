package com.snabbit.prototype.user.dto.response

import com.snabbit.prototype.user.service.UserNotFound
import com.snabbit.prototype.user.repository.UserProfileRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

data class CustomerContactSnapshot(val userId: UUID, val phoneNumber: String)
