package app.chenadet.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import app.chenadet.core.Place
import app.chenadet.data.LocationSource
import java.io.IOException
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.round
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

enum class LocationFailureReason { PERMISSION, DISABLED, UNAVAILABLE, TIMEOUT }
class LocationFailure(val reason: LocationFailureReason) : IOException("Location: $reason")

/** No subscription, foreground service, alarm or background permission. Caller cancels when Activity stops. */
class ForegroundLocation(context: Context) : LocationSource {
    private val app = context.applicationContext
    private val manager = app.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val legacyGeocoder = Executors.newSingleThreadExecutor { task -> Thread(task, "locality-lookup").apply { isDaemon = true } }
    @SuppressLint("MissingPermission") // Explicit permission check immediately before the single-shot request.
    override suspend fun locate(): Place {
        if (ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) throw LocationFailure(LocationFailureReason.PERMISSION)
        if (!LocationManagerCompat.isLocationEnabled(manager)) throw LocationFailure(LocationFailureReason.DISABLED)
        val provider = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER).firstOrNull {
            runCatching { manager.isProviderEnabled(it) }.getOrDefault(false)
        } ?: throw LocationFailure(LocationFailureReason.UNAVAILABLE)
        val location = withTimeoutOrNull(12_000) {
            suspendCancellableCoroutine<Location> { continuation ->
                val cancellation = CancellationSignal()
                continuation.invokeOnCancellation { cancellation.cancel() }
                try {
                    LocationManagerCompat.getCurrentLocation(manager, provider, cancellation, ContextCompat.getMainExecutor(app)) { result ->
                        if (continuation.isActive) {
                            if (result == null || SystemClock.elapsedRealtimeNanos() - result.elapsedRealtimeNanos > 120_000_000_000L) {
                                continuation.resumeWithException(LocationFailure(LocationFailureReason.UNAVAILABLE))
                            } else continuation.resume(result)
                        }
                    }
                } catch (_: SecurityException) {
                    if (continuation.isActive) continuation.resumeWithException(LocationFailure(LocationFailureReason.PERMISSION))
                } catch (_: IllegalArgumentException) {
                    if (continuation.isActive) continuation.resumeWithException(LocationFailure(LocationFailureReason.UNAVAILABLE))
                }
            }
        } ?: throw LocationFailure(LocationFailureReason.TIMEOUT)
        // Send only weather-cell precision, not raw device precision, to geocoding and weather services.
        val latitude = round(location.latitude * 100) / 100
        val longitude = round(location.longitude * 100) / 100
        val address = reverse(latitude, longitude)
        val name = address?.locality?.takeIf { it.isNotBlank() } ?: address?.subAdminArea?.takeIf { it.isNotBlank() } ?: "Моё местоположение"
        val region = address?.countryName.orEmpty()
        return Place(name, latitude, longitude, region)
    }
    private suspend fun reverse(lat: Double, lon: Double): Address? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(app, Locale.forLanguageTag("ru"))
        return withTimeoutOrNull(2_500) {
            suspendCancellableCoroutine { continuation ->
                fun complete(value: Address?) { if (continuation.isActive) continuation.resume(value) }
                if (Build.VERSION.SDK_INT >= 33) {
                    try {
                        geocoder.getFromLocation(lat, lon, 1, object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<Address>) { complete(addresses.firstOrNull()) }
                            override fun onError(errorMessage: String?) { complete(null) }
                        })
                    } catch (_: Exception) { complete(null) }
                } else {
                    val task = legacyGeocoder.submit {
                        @Suppress("DEPRECATION")
                        val value = runCatching { geocoder.getFromLocation(lat, lon, 1)?.firstOrNull() }.getOrNull()
                        complete(value)
                    }
                    continuation.invokeOnCancellation { task.cancel(true) }
                }
            }
        }
    }
}
