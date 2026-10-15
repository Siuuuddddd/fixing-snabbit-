package com.snabbit.prototype.user.dto.response

import java.util.UUID

data class CustomerSessionUser(
    val id: UUID,
    val name: String,
    val email: String,
    val pictureUrl: String?,
    val isNewUser: Boolean,
    val role: String = "CUSTOMER",
)
