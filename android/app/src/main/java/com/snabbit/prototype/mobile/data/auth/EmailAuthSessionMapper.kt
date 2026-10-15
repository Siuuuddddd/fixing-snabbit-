package com.snabbit.prototype.mobile.data.auth

import com.snabbit.prototype.mobile.data.auth.remote.SessionResponseDto
import com.snabbit.prototype.mobile.data.auth.remote.SessionUserDto
import com.snabbit.prototype.mobile.domain.auth.AuthSession
import com.snabbit.prototype.mobile.domain.auth.SessionUser

internal fun SessionUserDto.toDomain(phoneNumber: String? = null) = SessionUser(
    id = id,
    name = name,
    email = email,
    pictureUrl = pictureUrl,
    isNewUser = isNewUser,
    role = role,
    phoneNumber = phoneNumber,
    emailVerified = emailVerified,
    hasPassword = hasPassword,
    authProviders = authProviders.toSet(),
)

internal fun SessionResponseDto.toDomain(nowEpochSeconds: Long, phoneNumber: String? = null) = AuthSession(
    accessToken = accessToken,
    tokenType = tokenType,
    expiresAtEpochSeconds = nowEpochSeconds + expiresIn,
    refreshToken = refreshToken,
    user = user.toDomain(phoneNumber),
)

