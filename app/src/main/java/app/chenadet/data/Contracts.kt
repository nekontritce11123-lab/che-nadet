package app.chenadet.data

import app.chenadet.core.*
import java.time.Instant

/** Small interfaces allow tests to replace storage, location and HTTP independently. */
data class SavedSettings(val profile: UserProfile = UserProfile(), val mode: LocationMode = LocationMode.DEVICE,
    val place: Place? = null, val permissionAsked: Boolean = false)
data class CacheEntry(val placeKey: String, val rawJson: String, val fetchedAt: Instant)
data class WeatherLoad(val weather: WeatherConditions, val cacheSaved: Boolean = true)
interface SettingsStore {
    suspend fun read(): SavedSettings
    suspend fun saveProfile(profile: UserProfile)
    suspend fun savePlace(place: Place?, mode: LocationMode)
    suspend fun savePermissionAsked(asked: Boolean)
    suspend fun readCache(): CacheEntry?
    suspend fun saveCache(entry: CacheEntry)
    suspend fun clear()
}
interface WeatherRepository {
    suspend fun fetch(place: Place): WeatherLoad
    suspend fun cached(place: Place): WeatherConditions?
    suspend fun search(query: String): List<Place>
}
interface LocationSource { suspend fun locate(): Place }
