package app.chenadet.presentation

import app.chenadet.core.*
import java.time.Instant

data class HomeState(
    val initialized: Boolean = false,
    val place: Place? = null,
    val mode: LocationMode = LocationMode.DEVICE,
    val profile: UserProfile = UserProfile(),
    val weather: WeatherConditions? = null,
    val permission: PermissionStatus = PermissionStatus.NOT_ASKED,
    val loading: Boolean = false,
    val locating: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
    val locationDisabled: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<Place> = emptyList(),
    val searching: Boolean = false,
    val searchError: String? = null,
    val now: Instant = Instant.EPOCH,
) {
    val freshness: Freshness get() = weather?.let { FreshnessPolicy.evaluate(it, now) } ?: Freshness.HIDDEN
    val visibleWeather: WeatherConditions? get() = weather?.takeIf { freshness != Freshness.HIDDEN }
    val recommendation: Recommendation? get() = weather?.takeIf { freshness == Freshness.CURRENT || freshness == Freshness.STALE }
        ?.let { RecommendationEngine().recommend(it.copy(evaluationAt = now), profile) }
}
