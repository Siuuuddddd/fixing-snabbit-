package com.snabbit.prototype.notification.persistence

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
    fun findTop100ByCustomerIdAndIdGreaterThanOrderByIdAsc(customerId: UUID, id: Long): List<Notification>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select notification from Notification notification where notification.id = :id")
    fun findForUpdate(@Param("id") id: Long): Notification?
}

interface DeviceRegistrationRepository : JpaRepository<DeviceRegistration, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select device from DeviceRegistration device where device.pushToken = :token")
    fun findByTokenForUpdate(@Param("token") token: String): DeviceRegistration?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select device from DeviceRegistration device where device.id = :id")
    fun findForUpdate(@Param("id") id: UUID): DeviceRegistration?

    fun findAllByCustomerIdAndActiveTrue(customerId: UUID): List<DeviceRegistration>
}

interface NotificationDeliveryRepository : JpaRepository<NotificationDelivery, UUID>
