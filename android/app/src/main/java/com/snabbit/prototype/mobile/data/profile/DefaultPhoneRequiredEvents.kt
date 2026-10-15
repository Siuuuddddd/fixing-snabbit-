package com.snabbit.prototype.mobile.data.profile

import com.snabbit.prototype.mobile.domain.profile.PhoneRequiredEvents
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

class DefaultPhoneRequiredEvents : PhoneRequiredEvents {
    private val updates = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val events: Flow<Unit> = updates
    override fun notifyRequired() { updates.tryEmit(Unit) }
}
