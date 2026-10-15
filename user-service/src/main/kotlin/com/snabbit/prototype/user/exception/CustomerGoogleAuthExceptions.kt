package com.snabbit.prototype.user.exception

class CustomerAccountDisabled : RuntimeException("Customer account is not active")

class CustomerAccountConflict : RuntimeException("Customer account cannot be linked")
