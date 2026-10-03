package com.snabbit.prototype.user.exception

class AddressAccessDenied : RuntimeException("Authenticated customer does not own this address")
