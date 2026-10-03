package com.snabbit.prototype.notification.repository

import com.snabbit.prototype.notification.domain.NotificationDelivery
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

interface NotificationDeliveryRepository : JpaRepository<NotificationDelivery, UUID>
