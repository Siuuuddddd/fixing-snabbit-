package com.snabbit.prototype.mobile.domain.session

fun interface SessionIdentityProvider {
    fun customerId(): String?
}
