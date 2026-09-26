package app.chenadet.data

import app.chenadet.core.*
import java.io.IOException
import java.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DefaultWeatherRepository(private val api: WeatherApi, private val store: SettingsStore,
    private val clock: Clock = Clock.systemUTC()) : WeatherRepository {
    override suspend fun fetch(place: Place): WeatherLoad {
        val raw = api.weather(place)
        val fetchedAt = clock.instant()
        val weather = decode(raw, fetchedAt)
        val saved = try { store.saveCache(CacheEntry(place.key, raw, fetchedAt)); true }
        catch (e: CancellationException) { throw e }
        catch (_: IOException) { false }
        return WeatherLoad(weather, saved)
    }
    override suspend fun cached(place: Place): WeatherConditions? {
        val cache = store.readCache() ?: return null
        if (cache.placeKey != place.key) return null
        val weather = try { decode(cache.rawJson, cache.fetchedAt) }
        catch (e: CancellationException) { throw e }
        catch (_: InvalidWeatherData) { return null }
        return FreshnessPolicy.cacheFor(place, cache.placeKey, weather, clock.instant())
    }
    override suspend fun search(query: String): List<Place> {
        val raw = api.cities(query)
        return withContext(Dispatchers.Default) {
            try { OpenMeteo.cities(JsonAdapter.decode(raw)) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { throw InvalidWeatherData(e) }
        }
    }
    private suspend fun decode(raw: String, fetchedAt: java.time.Instant): WeatherConditions = withContext(Dispatchers.Default) {
        try { OpenMeteo.decode(JsonAdapter.decode(raw), fetchedAt) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { throw InvalidWeatherData(e) }
    }
}
