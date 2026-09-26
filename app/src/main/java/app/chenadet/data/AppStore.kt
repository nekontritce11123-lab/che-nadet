package app.chenadet.data

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import app.chenadet.core.*
import java.time.Instant
import kotlinx.coroutines.flow.first

private val Context.preferences by preferencesDataStore(name = "che_nadet",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() })

/** App-private DataStore, excluded from cloud backup and device transfer. One bounded weather entry. */
class AppStore(context: Context) : SettingsStore {
    private val data = context.applicationContext.preferences
    private object Keys {
        val sensitivity = stringPreferencesKey("sensitivity")
        val activity = stringPreferencesKey("activity")
        val mode = stringPreferencesKey("location_mode")
        val name = stringPreferencesKey("place_name")
        val region = stringPreferencesKey("place_region")
        val latitude = doublePreferencesKey("latitude")
        val longitude = doublePreferencesKey("longitude")
        val asked = booleanPreferencesKey("permission_asked")
        val cacheKey = stringPreferencesKey("weather_place_key")
        val cacheJson = stringPreferencesKey("weather_json")
        val cacheAt = longPreferencesKey("weather_fetched_at")
    }
    override suspend fun read(): SavedSettings {
        val p = data.data.first()
        val profile = UserProfile(Sensitivity.values().firstOrNull { it.name == p[Keys.sensitivity] } ?: Sensitivity.NORMAL,
            Activity.values().firstOrNull { it.name == p[Keys.activity] } ?: Activity.CITY)
        val mode = LocationMode.values().firstOrNull { it.name == p[Keys.mode] } ?: LocationMode.DEVICE
        val name = p[Keys.name]; val lat = p[Keys.latitude]; val lon = p[Keys.longitude]
        val place = if (name != null && lat != null && lon != null) runCatching { Place(name, lat, lon, p[Keys.region].orEmpty()) }.getOrNull() else null
        return SavedSettings(profile, mode, place, p[Keys.asked] ?: false)
    }
    override suspend fun saveProfile(profile: UserProfile) {
        data.edit { it[Keys.sensitivity] = profile.sensitivity.name; it[Keys.activity] = profile.activity.name }
    }
    override suspend fun savePlace(place: Place?, mode: LocationMode) {
        data.edit { p ->
            p[Keys.mode] = mode.name
            if (place == null) {
                p.remove(Keys.name); p.remove(Keys.region); p.remove(Keys.latitude); p.remove(Keys.longitude)
            } else {
                p[Keys.name] = place.name; p[Keys.region] = place.region
                p[Keys.latitude] = place.latitude; p[Keys.longitude] = place.longitude
            }
        }
    }
    override suspend fun savePermissionAsked(asked: Boolean) { data.edit { it[Keys.asked] = asked } }
    override suspend fun readCache(): CacheEntry? {
        val p = data.data.first()
        val key = p[Keys.cacheKey] ?: return null; val json = p[Keys.cacheJson] ?: return null
        val at = p[Keys.cacheAt]?.let { runCatching { Instant.ofEpochSecond(it) }.getOrNull() } ?: return null
        return CacheEntry(key, json, at)
    }
    override suspend fun saveCache(entry: CacheEntry) {
        require(entry.rawJson.length <= 1_048_576)
        data.edit { it[Keys.cacheKey] = entry.placeKey; it[Keys.cacheJson] = entry.rawJson; it[Keys.cacheAt] = entry.fetchedAt.epochSecond }
    }
    override suspend fun clear() { data.edit { it.clear() } }
}
