package com.snabbit.prototype.user.service

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

data class AddressCommand(
    val label: String,
    val addressLine: String,
    val city: String,
    val pincode: String,
    val latitude: BigDecimal,
    val longitude: BigDecimal,
    val locationAccuracyMeters: BigDecimal,
    val locationCapturedAt: Instant,
)

data class AddressSnapshot(
    val addressId: UUID,
    val userId: UUID,
    val label: String,
    val addressLine: String,
    val city: String,
    val pincode: String,
    val latitude: BigDecimal,
    val longitude: BigDecimal,
    val verified: Boolean,
)

class AddressNotFound : RuntimeException("Address was not found")
class AddressAccessDenied : RuntimeException("Authenticated customer does not own this address")
class AddressRejected(message: String) : RuntimeException(message)

@Service
class AddressService(
    private val users: UserProfileRepository,
    private val addresses: UserAddressRepository,
    private val clock: Clock,
    private val writes: AddressWriteTransaction,
    private val locationVerifier: AddressLocationVerifier,
) {
    @Transactional(readOnly = true)
    fun list(userId: UUID): List<AddressSnapshot> {
        requireUser(userId)
        return addresses.findAllByUserIdOrderByCreatedAtDesc(userId).map(::snapshot)
    }

    fun create(userId: UUID, command: AddressCommand): AddressSnapshot {
        val now = clock.instant()
        validate(command, now)
        return snapshot(writes.create(userId, command, now))
    }

    fun delete(userId: UUID, addressId: UUID) {
        writes.delete(userId, addressId)
    }

    @Transactional(readOnly = true)
    fun requireOwnedVerified(userId: UUID, addressId: UUID): AddressSnapshot {
        val address = addresses.findById(addressId).orElseThrow(::AddressNotFound)
        if (address.userId != userId) throw AddressAccessDenied()
        if (!address.verified) throw AddressRejected("Address location is not verified")
        return snapshot(address)
    }

    private fun requireUser(userId: UUID) {
        if (!users.existsById(userId)) throw UserNotFound(userId)
    }

    private fun validate(command: AddressCommand, now: Instant) {
        if (command.label.trim().length !in 1..60) throw AddressRejected("Address label must contain 1 to 60 characters")
        if (command.addressLine.trim().length !in 5..300) {
            throw AddressRejected("Address line must contain 5 to 300 characters")
        }
        if (!command.city.trim().equals("Mumbai", ignoreCase = true)) {
            throw AddressRejected("Prototype service is currently available only in Mumbai")
        }
        if (!MUMBAI_PINCODE.matches(command.pincode)) throw AddressRejected("A valid Mumbai pincode is required")
        if (command.latitude !in MIN_LATITUDE..MAX_LATITUDE || command.longitude !in MIN_LONGITUDE..MAX_LONGITUDE) {
            throw AddressRejected("Address coordinates must be inside the Mumbai service area")
        }
        if (command.locationAccuracyMeters <= BigDecimal.ZERO || command.locationAccuracyMeters > MAX_ACCURACY_METERS) {
            throw AddressRejected("Address location accuracy must be within 100 metres")
        }
        if (command.locationCapturedAt.isBefore(now.minus(MAX_LOCATION_AGE)) ||
            command.locationCapturedAt.isAfter(now.plus(MAX_FUTURE_SKEW))
        ) {
            throw AddressRejected("Address location must be captured recently")
        }
        locationVerifier.verify(
            command.addressLine.trim(), command.city.trim(), command.pincode,
            command.latitude, command.longitude,
        )
    }

    private fun snapshot(address: UserAddress) = AddressSnapshot(
        address.id,
        address.userId,
        address.label,
        address.addressLine,
        address.city,
        address.pincode,
        address.latitude,
        address.longitude,
        address.verified,
    )

    private companion object {
        val MUMBAI_PINCODE = Regex("^400[0-9]{3}$")
        val MIN_LATITUDE = BigDecimal("18.85")
        val MAX_LATITUDE = BigDecimal("19.35")
        val MIN_LONGITUDE = BigDecimal("72.75")
        val MAX_LONGITUDE = BigDecimal("73.10")
        val MAX_ACCURACY_METERS = BigDecimal("100")
        val MAX_LOCATION_AGE: Duration = Duration.ofMinutes(5)
        val MAX_FUTURE_SKEW: Duration = Duration.ofSeconds(30)
    }
}

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
