package com.snabbit.prototype.booking.dto.request

import java.time.LocalDate
import java.time.LocalTime

data class RequestedVisit(val date: LocalDate, val startTime: LocalTime)
