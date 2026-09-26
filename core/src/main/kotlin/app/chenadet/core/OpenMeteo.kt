package app.chenadet.core

import java.net.URLEncoder
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/** Pure provider boundary; JSON decoding/network/Android are adapters outside the rule engine. */
object OpenMeteo {
    private val currentFields = listOf("temperature_2m", "apparent_temperature", "relative_humidity_2m", "wind_speed_10m",
        "wind_gusts_10m", "precipitation", "rain", "showers", "snowfall", "weather_code", "is_day", "cloud_cover")
    private val hourlyFields = currentFields.filterNot { it == "cloud_cover" } + listOf("uv_index", "precipitation_probability")
    private fun encode(s: String): String = URLEncoder.encode(s, "UTF-8")
    fun weatherUrl(place: Place): String {
        val lat = String.format(Locale.ROOT, "%.2f", place.latitude)
        val lon = String.format(Locale.ROOT, "%.2f", place.longitude)
        return "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&current=${currentFields.joinToString(",")}&hourly=${hourlyFields.joinToString(",")}" +
            "&wind_speed_unit=ms&temperature_unit=celsius&precipitation_unit=mm" +
            "&timeformat=unixtime&timezone=auto&forecast_hours=8&past_hours=1"
    }
    fun cityUrl(query: String): String {
        val text = query.trim()
        require(text.length in 2..100) { "City query must be 2–100 characters" }
        return "https://geocoding-api.open-meteo.com/v1/search?name=${encode(text)}&count=10&language=ru&format=json"
    }
    fun decode(root: Map<String, Any?>, fetchedAt: Instant): WeatherConditions {
        require(root["error"] != true) { "Weather API rejected the request" }
        val current = root.obj("current") ?: error("Missing current conditions")
        val units = root.obj("current_units") ?: error("Missing current units")
        val currentTime = epoch(current["time"]) ?: error("Missing current timestamp")
        require(units["time"] == "unixtime") { "Unexpected time unit" }
        val interval = number(current["interval"])?.takeIf { it > 0 && it <= 3600 }
        val currentPoint = point(current, units, currentTime, interval?.let { 3600.0 / it })
            ?: error("Missing or invalid current temperature")
        val hourly = root.obj("hourly")
        val hourlyUnits = root.obj("hourly_units") ?: emptyMap()
        val times = (hourly?.get("time") as? List<*>) ?: emptyList<Any?>()
        if (times.isNotEmpty()) require(hourlyUnits["time"] == "unixtime") { "Unexpected hourly timestamp unit" }
        val points = times.mapIndexedNotNull { index, rawTime ->
            val at = epoch(rawTime) ?: return@mapIndexedNotNull null
            val row = hourly!!.mapValues { (_, value) -> (value as? List<*>)?.getOrNull(index) }
            point(row, hourlyUnits, at, 1.0)
        }.sortedBy { it.at }.distinctBy { it.at }
        val uvSample = points.lastOrNull { it.at <= currentTime && currentTime.epochSecond - it.at.epochSecond < 3600 }
        // Probability applies to the precipitation interval ending at this next hourly boundary.
        val nextInterval = points.firstOrNull { it.at > currentTime && it.at.epochSecond - currentTime.epochSecond <= 3600 }
        val timezone = (root["timezone"] as? String)?.takeIf { runCatching { ZoneId.of(it) }.isSuccess } ?: "UTC"
        return WeatherConditions(currentPoint.copy(uv = uvSample?.uv, probabilityPct = nextInterval?.probabilityPct, probabilityUntil = nextInterval?.at), points, fetchedAt, timezone)
    }
    private fun point(row: Map<String, Any?>, units: Map<String, Any?>, at: Instant, amountMultiplier: Double?): WeatherPoint? {
        fun n(name: String, unit: String? = null): Double? {
            val value = number(row[name]) ?: return null
            if (unit != null) require(units[name] == unit) { "Unexpected unit for $name" }
            return value
        }
        fun amount(name: String, unit: String): Double? {
            val total = n(name, unit)?.takeIf { it >= 0 } ?: return null
            return amountMultiplier?.let { total * it }
        }
        val temp = n("temperature_2m", "°C").valid(-100.0, 65.0) ?: return null
        val rain = amount("rain", "mm"); val showers = amount("showers", "mm")
        return WeatherPoint(at, temp, n("apparent_temperature", "°C"), n("relative_humidity_2m", "%")?.toInt(),
            n("wind_speed_10m", "m/s"), n("wind_gusts_10m", "m/s"), amount("precipitation", "mm"),
            if (rain != null && showers != null) rain + showers else null, amount("snowfall", "cm"),
            n("precipitation_probability", "%")?.toInt(), n("weather_code")?.toInt(), n("uv_index"),
            when (n("is_day")) { 0.0 -> false; 1.0 -> true; else -> null }, n("cloud_cover", "%")?.toInt()).normalized()
    }
    fun cities(root: Map<String, Any?>): List<Place> {
        require(root["error"] != true) { "City search service rejected request" }
        return ((root["results"] as? List<*>) ?: emptyList<Any?>()).mapNotNull { raw ->
            val row = raw as? Map<*, *> ?: return@mapNotNull null
            val name = (row["name"] as? String)?.trim()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val lat = number(row["latitude"]) ?: return@mapNotNull null
            val lon = number(row["longitude"]) ?: return@mapNotNull null
            val region = listOfNotNull(row["admin1"] as? String, row["country"] as? String).filter { it != name && it.isNotBlank() }.distinct().joinToString(", ")
            runCatching { Place(name, lat, lon, region) }.getOrNull()
        }.distinctBy { it.key }.take(10)
    }
    private fun number(value: Any?): Double? = (value as? Number)?.toDouble()?.takeIf { it.isFinite() }
    private fun epoch(value: Any?): Instant? = number(value)?.takeIf { it in 0.0..4102444800.0 && it % 1.0 == 0.0 }?.let { Instant.ofEpochSecond(it.toLong()) }
    private fun Map<String, Any?>.obj(key: String): Map<String, Any?>? {
        val value = get(key) as? Map<*, *> ?: return null
        return value.entries.filter { it.key is String }.associate { it.key as String to it.value }
    }
}
