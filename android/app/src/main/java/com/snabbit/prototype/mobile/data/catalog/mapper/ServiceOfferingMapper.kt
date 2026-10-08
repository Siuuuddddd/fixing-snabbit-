package com.snabbit.prototype.mobile.data.catalog.mapper

import com.snabbit.prototype.mobile.data.catalog.remote.ServiceOfferingDto
import com.snabbit.prototype.mobile.domain.catalog.ServiceOffering

fun ServiceOfferingDto.toDomain() = ServiceOffering(
    id = serviceId,
    code = code,
    displayName = displayName,
    basePricePerHourMinor = basePricePerHourMinor,
)
