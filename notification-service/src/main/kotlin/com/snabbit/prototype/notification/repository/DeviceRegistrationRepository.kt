package com.snabbit.prototype.notification.repository

import com.snabbit.prototype.notification.domain.DeviceRegistration
import com.snabbit.prototype.notification.domain.enums.RecipientType
import jakarta.persistence.LockModeType
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface DeviceRegistrationRepository : JpaRepository<DeviceRegistration, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select device from DeviceRegistration device where device.pushToken = :token")
    fun findByTokenForUpdate(@Param("token") token: String): DeviceRegistration?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select device from DeviceRegistration device where device.id = :id")
    fun findForUpdate(@Param("id") id: UUID): DeviceRegistration?

    fun findAllByRecipientTypeAndRecipientIdAndActiveTrue(
        recipientType: RecipientType,
        recipientId: UUID,
    ): List<DeviceRegistration>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select device from DeviceRegistration device where device.pushToken in :tokens")
    fun findAllByPushTokenInForUpdate(@Param("tokens") tokens: Collection<String>): List<DeviceRegistration>
}
