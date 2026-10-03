package com.snabbit.prototype.booking.exception

class CustomerAccessDenied : RuntimeException("Authenticated customer does not own this resource")
