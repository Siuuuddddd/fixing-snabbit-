package com.snabbit.prototype.mobile.domain.profile

import kotlinx.coroutines.flow.Flow

interface PhoneRequiredEvents {
    val events: Flow<Unit>
    fun notifyRequired()
}
