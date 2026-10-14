package com.snabbit.prototype.worker.service

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import org.springframework.stereotype.Component

@Component
class WorkerLocationPolicy {
    fun evaluate(
        addressLatitude: Double,
        addressLongitude: Double,
        workerLatitude: Double,
        workerLongitude: Double,
        requestRadiusKm: Double,
        workerRadiusKm: Double,
    ): WorkerLocationResult {
        val distance = distanceKm(addressLatitude, addressLongitude, workerLatitude, workerLongitude)
        return WorkerLocationResult(
            distanceKm = distance,
            displayDistanceKm = BigDecimal.valueOf(distance).setScale(1, RoundingMode.HALF_UP),
            inRange = distance <= requestRadiusKm && distance <= workerRadiusKm,
        )
    }

    private fun distanceKm(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): Double {
        val earthRadiusKm = 6_371.0
        val latitudeDistance = Math.toRadians(toLat - fromLat)
        val longitudeDistance = Math.toRadians(toLon - fromLon)
        val a = sin(latitudeDistance / 2) * sin(latitudeDistance / 2) +
            cos(Math.toRadians(fromLat)) * cos(Math.toRadians(toLat)) *
            sin(longitudeDistance / 2) * sin(longitudeDistance / 2)
        return earthRadiusKm * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}
