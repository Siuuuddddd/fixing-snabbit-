package com.snabbit.prototype.user.exception

class CustomerAccessDenied : RuntimeException("Authenticated customer does not own this resource")
