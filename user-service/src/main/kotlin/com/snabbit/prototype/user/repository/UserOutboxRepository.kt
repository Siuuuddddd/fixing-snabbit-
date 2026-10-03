package com.snabbit.prototype.user.repository

import com.snabbit.prototype.user.domain.UserOutboxEvent
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

interface UserOutboxRepository : JpaRepository<UserOutboxEvent, UUID>
