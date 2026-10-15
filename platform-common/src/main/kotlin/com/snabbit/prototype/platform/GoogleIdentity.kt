package com.snabbit.prototype.platform

data class GoogleIdentity(
    val subject: String,
    val email: String,
    val displayName: String?,
    val pictureUrl: String?,
)
