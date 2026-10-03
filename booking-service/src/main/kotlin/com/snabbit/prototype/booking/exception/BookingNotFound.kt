package com.snabbit.prototype.booking.exception

import java.util.UUID

class BookingNotFound(id: UUID) : RuntimeException("Booking $id was not found")
