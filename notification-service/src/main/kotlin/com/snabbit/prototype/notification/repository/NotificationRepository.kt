package com.snabbit.prototype.notification.repository

import com.snabbit.prototype.notification.domain.enums.RecipientType

import com.snabbit.prototype.notification.domain.DeviceRegistration
import com.snabbit.prototype.notification.domain.Notification
import com.snabbit.prototype.notification.domain.NotificationDelivery
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface NotificationRepository : JpaRepository<Notification, Long> {
    fun findByEventId(eventId: UUID): Notification?
    fun findTop100ByRecipientTypeAndRecipientIdAndIdGreaterThanOrderByIdAsc(
        recipientType: RecipientType,
        recipientId: UUID,
        id: Long,
    ): List<Notification>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select notification from Notification notification where notification.id = :id")
    fun findForUpdate(@Param("id") id: Long): Notification?
}
