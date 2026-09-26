package app.chenadet.core

import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong

enum class Freshness { CURRENT, STALE, EXPIRED, HIDDEN }
object FreshnessPolicy {
    fun evaluate(weather: WeatherConditions, now: Instant): Freshness {
        val downloadAge = Duration.between(weather.fetchedAt, now).seconds
        val modelAge = Duration.between(weather.current.at, now).seconds
        return when {
            downloadAge < -300 || modelAge < -300 -> Freshness.HIDDEN
            downloadAge > 24 * 3600 || modelAge > 24 * 3600 -> Freshness.HIDDEN
            downloadAge > 6 * 3600 || modelAge > 6 * 3600 -> Freshness.EXPIRED
            downloadAge > 30 * 60 || modelAge > 90 * 60 -> Freshness.STALE
            else -> Freshness.CURRENT
        }
    }
    fun cacheFor(place: Place, storedKey: String, weather: WeatherConditions?, now: Instant): WeatherConditions? =
        weather?.takeIf { place.key == storedKey && evaluate(it, now) != Freshness.HIDDEN }
}
/** Used in addition to cancellation: a slow/non-cancellable provider must not win an old request. */
class RequestGate {
    private val generation = AtomicLong()
    fun next(): Long = generation.incrementAndGet()
    fun accepts(id: Long): Boolean = generation.get() == id
}
enum class PermissionStatus { NOT_ASKED, DENIED, PERMANENTLY_DENIED, GRANTED }
fun permissionStatus(granted: Boolean, asked: Boolean, showRationale: Boolean): PermissionStatus = when {
    granted -> PermissionStatus.GRANTED
    !asked -> PermissionStatus.NOT_ASKED
    showRationale -> PermissionStatus.DENIED
    else -> PermissionStatus.PERMANENTLY_DENIED
}
