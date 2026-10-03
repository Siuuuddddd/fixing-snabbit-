package com.snabbit.prototype.user.repository

import com.snabbit.prototype.user.domain.UserOutboxEvent
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface UserOutboxRepository : JpaRepository<UserOutboxEvent, UUID>
