package com.snabbit.prototype.user.dto.response

import java.util.UUID

data class CustomerSessionUser(
    val id: UUID,
    val name: String,
    val email: String?,
    val phoneNumber: String?,
    val pictureUrl: String?,
    val isNewUser: Boolean,
    val emailVerified: Boolean,
    val hasPassword: Boolean,
    val authProviders: Set<String>,
    val role: String = "CUSTOMER",
)
