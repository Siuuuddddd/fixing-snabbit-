package com.snabbit.prototype.user.service

import com.snabbit.prototype.user.dto.request.AddressCommand

import com.snabbit.prototype.user.domain.UserAddress
import com.snabbit.prototype.user.events.UserOutboxWriter
import com.snabbit.prototype.user.location.AddressLocationVerifier
import com.snabbit.prototype.user.repository.UserAddressRepository
import com.snabbit.prototype.user.repository.UserProfileRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Service
class AddressWriteTransaction(
    private val users: UserProfileRepository,
    private val addresses: UserAddressRepository,
    private val outbox: UserOutboxWriter,
    private val clock: Clock,
) {
    @Transactional
    fun create(userId: UUID, command: AddressCommand, now: Instant): UserAddress {
        if (users.findForUpdate(userId) == null) throw UserNotFound(userId)
        if (addresses.countByUserId(userId) >= MAX_ADDRESSES) {
            throw AddressRejected("A customer can save at most $MAX_ADDRESSES addresses")
        }
        val saved = addresses.save(
            UserAddress(
                UUID.randomUUID(), userId, command.label.trim(), command.addressLine.trim(), "Mumbai", command.pincode,
                command.latitude.setScale(6, RoundingMode.HALF_UP),
                command.longitude.setScale(6, RoundingMode.HALF_UP), true, now,
            ),
        )
        outbox.append(
            userId, "UserAddressCreated",
            mapOf("userId" to userId, "addressId" to saved.id, "city" to saved.city, "pincode" to saved.pincode),
            now,
        )
        return saved
    }

    @Transactional
    fun delete(userId: UUID, addressId: UUID) {
        val address = addresses.findById(addressId).orElseThrow(::AddressNotFound)
        if (address.userId != userId) throw AddressAccessDenied()
        addresses.delete(address)
        outbox.append(
            userId, "UserAddressDeleted", mapOf("userId" to userId, "addressId" to addressId), clock.instant(),
        )
    }

    private companion object {
        const val MAX_ADDRESSES = 10L
    }
}
