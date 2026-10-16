package com.snabbit.prototype.user.auth

import com.snabbit.prototype.platform.IndianPhoneNumber
import com.snabbit.prototype.platform.OpaqueToken
import com.snabbit.prototype.platform.PhoneVerificationProvider
import com.snabbit.prototype.platform.VerificationStatus
import com.snabbit.prototype.user.config.DemoCustomerDevSeed
import com.snabbit.prototype.user.config.UserDevSeedProperties
import com.snabbit.prototype.user.domain.UserProfile
import com.snabbit.prototype.user.domain.enums.UserAuthProvider
import com.snabbit.prototype.user.domain.enums.UserStatus
import com.snabbit.prototype.user.dto.request.PhoneOtpVerifyRequest
import com.snabbit.prototype.user.dto.response.CustomerSessionResponse
import com.snabbit.prototype.user.exception.CustomerAccountDisabled
import com.snabbit.prototype.user.exception.InvalidOrExpiredCode
import com.snabbit.prototype.user.exception.TooManyCodeAttempts
import com.snabbit.prototype.user.repository.UserProfileRepository
import java.time.Clock
import java.util.UUID
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CustomerPhoneAuthService(
    private val provider: PhoneVerificationProvider,
    private val challengeStore: OtpChallengeStore,
    private val rateLimiter: PhoneAuthRateLimiter,
    private val users: UserProfileRepository,
    private val sessions: CustomerSessionService,
    private val phoneProperties: PhoneAuthProperties,
    private val authProperties: AuthProperties,
    private val devSeedProperties: UserDevSeedProperties,
    private val demoSeed: ObjectProvider<DemoCustomerDevSeed>,
    private val clock: Clock,
) {
    fun request(rawPhone: String, ipAddress: String): Long {
        val phone = IndianPhoneNumber.normalize(rawPhone)
        rateLimiter.acquireSend(phone, ipAddress)
        return try {
            challengeStore.clear(challengeKey(phone))
            val result = provider.start(phone)
            logger.info("Customer verification requested for {}", masked(phone))
            minOf(result.expiresInSeconds, phoneProperties.resendCooldown.seconds)
        } catch (error: RuntimeException) {
            rateLimiter.releaseFailedSend(phone)
            throw error
        }
    }

    @Transactional
    fun verify(request: PhoneOtpVerifyRequest, ipAddress: String): CustomerSessionResponse {
        val phone = IndianPhoneNumber.normalize(request.phoneNumber)
        rateLimiter.checkVerification(ipAddress)
        val key = challengeKey(phone)
        return when (provider.check(phone, request.code)) {
            VerificationStatus.APPROVED -> {
                challengeStore.clear(key)
                provider.clear(phone)
                val resolved = resolveCustomer(phone, request.name)
                sessions.create(
                    resolved.user,
                    request.deviceId,
                    request.deviceName,
                    resolved.isNewUser,
                    request.rememberMe,
                )
            }
            VerificationStatus.WRONG_CODE -> {
                val attempts = challengeStore.incrementAttempts(key, authProperties.otpTtl)
                if (attempts >= phoneProperties.maxAttempts) {
                    challengeStore.clear(key)
                    provider.clear(phone)
                    throw TooManyCodeAttempts()
                }
                throw InvalidOrExpiredCode()
            }
            VerificationStatus.EXPIRED -> {
                challengeStore.clear(key)
                throw InvalidOrExpiredCode()
            }
            VerificationStatus.MAX_ATTEMPTS -> {
                challengeStore.clear(key)
                provider.clear(phone)
                throw TooManyCodeAttempts()
            }
        }
    }

    private fun resolveCustomer(phone: String, requestedName: String?): ResolvedCustomer {
        users.findByPhoneForUpdate(phone)?.let { existing ->
            if (existing.status != UserStatus.ACTIVE) throw CustomerAccountDisabled()
            updateLogin(existing, requestedName)
            demoSeed.ifAvailable?.ensureFor(existing.id)
            return ResolvedCustomer(existing, false)
        }

        configuredDemo(phone)?.let { demo ->
            updateLogin(demo, requestedName)
            demoSeed.ifAvailable?.ensureFor(demo.id)
            return ResolvedCustomer(demo, false)
        }

        val now = clock.instant()
        val name = requestedName?.trim()?.takeIf(String::isNotEmpty) ?: "Snabbit customer"
        val user = users.save(
            UserProfile(
                id = UUID.randomUUID(),
                phoneE164 = phone,
                displayName = name,
                status = UserStatus.ACTIVE,
                plusActive = false,
                plusExpiresAt = null,
                createdAt = now,
                updatedAt = now,
                authProvider = UserAuthProvider.PHONE,
                authProviders = mutableSetOf(UserAuthProvider.PHONE),
                lastLoginAt = now,
            ),
        )
        return ResolvedCustomer(user, true)
    }

    private fun configuredDemo(phone: String): UserProfile? {
        if (!devSeedProperties.enabled || devSeedProperties.demoCustomerPhone != phone) return null
        val demo = demoSeed.ifAvailable?.ensure() ?: return null
        require(demo.phoneE164 == null || demo.phoneE164 == phone) { "Configured demo customer already has another phone" }
        demo.phoneE164 = phone
        demo.authProvider = UserAuthProvider.PHONE
        demo.authProviders.add(UserAuthProvider.PHONE)
        return demo
    }

    private fun updateLogin(user: UserProfile, requestedName: String?) {
        val now = clock.instant()
        requestedName?.trim()?.takeIf(String::isNotEmpty)?.let { user.displayName = it }
        user.authProviders.add(UserAuthProvider.PHONE)
        user.phoneVerifiedAt = user.phoneVerifiedAt ?: now
        user.authProvider = UserAuthProvider.PHONE
        user.lastLoginAt = now
        user.updatedAt = now
    }

    private fun challengeKey(phone: String): String = "customer:${OpaqueToken.hash(phone)}"
    private fun masked(phone: String): String = "+91******${phone.takeLast(4)}"

    private data class ResolvedCustomer(val user: UserProfile, val isNewUser: Boolean)

    companion object { private val logger = LoggerFactory.getLogger(CustomerPhoneAuthService::class.java) }
}
