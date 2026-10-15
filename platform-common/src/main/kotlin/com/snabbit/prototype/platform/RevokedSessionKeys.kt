package com.snabbit.prototype.platform

import java.util.UUID

object RevokedSessionKeys {
    fun key(sessionId: UUID): String = "auth:revoked-sid:$sessionId"
}
