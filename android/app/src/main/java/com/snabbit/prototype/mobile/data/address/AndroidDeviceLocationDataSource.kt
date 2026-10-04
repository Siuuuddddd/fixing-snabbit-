package com.snabbit.prototype.mobile.data.address

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import com.snabbit.prototype.mobile.domain.address.DeviceLocationDataSource
import com.snabbit.prototype.mobile.domain.address.LocationFailure
import com.snabbit.prototype.mobile.domain.address.LocationFailureReason
import com.snabbit.prototype.mobile.domain.address.LocationSample
import java.time.Instant
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout

class AndroidDeviceLocationDataSource(
    private val context: Context,
) : DeviceLocationDataSource {
    private val locationManager = context.getSystemService(LocationManager::class.java)

    @SuppressLint("MissingPermission")
    override suspend fun currentLocation(): LocationSample {
        if (!hasLocationPermission()) throw LocationFailure(LocationFailureReason.PERMISSION_REQUIRED)
        val provider = enabledProvider() ?: throw LocationFailure(LocationFailureReason.UNAVAILABLE)
        val location = try {
            withTimeout(LOCATION_TIMEOUT_MILLIS) {
                suspendCancellableCoroutine<Location> { continuation ->
                    val listener = object : LocationListener {
                        override fun onLocationChanged(location: Location) {
                            locationManager.removeUpdates(this)
                            if (continuation.isActive) continuation.resume(location)
                        }

                        @Deprecated("Deprecated by Android")
                        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
                        override fun onProviderEnabled(provider: String) = Unit
                        override fun onProviderDisabled(provider: String) = Unit
                    }
                    continuation.invokeOnCancellation { locationManager.removeUpdates(listener) }
                    runCatching {
                        locationManager.requestLocationUpdates(
                            provider,
                            0L,
                            0f,
                            listener,
                            Looper.getMainLooper(),
                        )
                    }.onFailure { failure ->
                        locationManager.removeUpdates(listener)
                        if (continuation.isActive) continuation.resumeWithException(failure)
                    }
                }
            }
        } catch (_: TimeoutCancellationException) {
            throw LocationFailure(LocationFailureReason.TIMEOUT)
        } catch (_: SecurityException) {
            throw LocationFailure(LocationFailureReason.PERMISSION_REQUIRED)
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: Exception) {
            throw LocationFailure(LocationFailureReason.UNAVAILABLE)
        }
        return LocationSample(
            latitude = location.latitude,
            longitude = location.longitude,
            accuracyMeters = location.accuracy.toDouble(),
            capturedAtIso = Instant.ofEpochMilli(location.time).toString(),
        )
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun enabledProvider(): String? = listOf(
        LocationManager.GPS_PROVIDER,
        LocationManager.NETWORK_PROVIDER,
    ).firstOrNull { provider ->
        runCatching { locationManager.isProviderEnabled(provider) }.getOrDefault(false)
    }

    private companion object {
        const val LOCATION_TIMEOUT_MILLIS = 15_000L
    }
}
