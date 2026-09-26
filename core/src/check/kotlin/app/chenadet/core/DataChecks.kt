package app.chenadet.core

import java.time.Instant
import java.util.Locale

fun additionalChecks() = with(CoreChecks) {
    test("cache freshness has explicit current stale expired and hidden states") {
        fun age(seconds: Long) = WeatherConditions(point().copy(at = time.minusSeconds(seconds)), fetchedAt = time.minusSeconds(seconds))
        expect(FreshnessPolicy.evaluate(age(0), time) == Freshness.CURRENT, "fresh")
        expect(FreshnessPolicy.evaluate(age(31 * 60), time) == Freshness.STALE, "31 minutes")
        expect(FreshnessPolicy.evaluate(age(6 * 3600 + 1), time) == Freshness.EXPIRED, "6 hours")
        expect(FreshnessPolicy.evaluate(age(24 * 3600 + 1), time) == Freshness.HIDDEN, "24 hours")
    }
    test("recent download of old model data is still stale") {
        val oldModel = WeatherConditions(point().copy(at = time.minusSeconds(100 * 60)), fetchedAt = time)
        expect(FreshnessPolicy.evaluate(oldModel, time) == Freshness.STALE, "fetch != observation")
    }
    test("future timestamp from wrong clock cannot be fresh") {
        val future = WeatherConditions(point().copy(at = time.plusSeconds(600)), fetchedAt = time.plusSeconds(600))
        expect(FreshnessPolicy.evaluate(future, time) == Freshness.HIDDEN, "clock skew")
    }
    test("cache never leaks weather from a previous city") {
        val a = Place("A", 50.0, 40.0); val b = Place("B", 55.0, 41.0)
        expect(FreshnessPolicy.cacheFor(a, b.key, WeatherConditions(point()), time) == null, "cross city cache")
        expect(FreshnessPolicy.cacheFor(a, a.key, WeatherConditions(point()), time) != null, "same city cache")
    }
    test("late network request cannot replace a newer request") {
        val gate = RequestGate(); val old = gate.next(); val latest = gate.next()
        expect(!gate.accepts(old), "old request"); expect(gate.accepts(latest), "latest request")
        gate.next(); expect(!gate.accepts(latest), "cancel invalidates generation")
    }
    test("permission states distinguish first prompt refusal and settings recovery") {
        expect(permissionStatus(true, true, false) == PermissionStatus.GRANTED, "granted")
        expect(permissionStatus(false, false, false) == PermissionStatus.NOT_ASKED, "never asked")
        expect(permissionStatus(false, true, true) == PermissionStatus.DENIED, "rationale")
        expect(permissionStatus(false, true, false) == PermissionStatus.PERMANENTLY_DENIED, "settings recovery")
    }
    test("all garments accessories reasons and warnings have concise Russian text") {
        Garment.values().forEach { expect(WeatherText.garment(it).isNotBlank(), "garment $it") }
        Accessory.values().forEach { expect(WeatherText.accessory(it).isNotBlank(), "accessory $it") }
        Reason.values().forEach { expect(WeatherText.reason(it).isNotBlank(), "reason $it") }
        Hazard.values().forEach { expect(WeatherText.warning(WeatherWarning(it, Severity.CAUTION)).isNotBlank(), "hazard $it") }
    }
    test("missing metrics are not rendered as a fabricated zero") {
        val p = point().copy(humidityPct = null, windMs = null, probabilityPct = null, uv = null)
        val m = WeatherText.metrics(p, result(p))
        expect(m.size >= 8, "metric coverage")
        expect(m.any { it.title == "Влажность" && it.value == "Нет данных" }, "RH unknown")
        expect(m.all { it.explanation.isNotBlank() }, "all metrics explainable")
    }
    test("weather URL uses SI unix timestamps bounded hours and no API key") {
        val u = OpenMeteo.weatherUrl(Place("A", 55.25, 37.75))
        expect(u.startsWith("https://api.open-meteo.com/v1/forecast?"), "real API")
        expect("wind_speed_unit=ms" in u && "timeformat=unixtime" in u && "forecast_hours=8" in u, "units/time/window")
        expect("apikey" !in u.lowercase() && "pressure" !in u, "no secrets or unused fields")
    }
    test("URL coordinates and cache identity ignore device locale") {
        val old = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            val p = Place("A", 55.25, 37.75)
            expect("latitude=55.25" in OpenMeteo.weatherUrl(p), "decimal dot")
            expect(p.key == "55.25,37.75", "stable cache key")
        } finally { Locale.setDefault(old) }
    }
    test("manual city input is encoded not interpreted as URL parameters") {
        val u = OpenMeteo.cityUrl("Москва &count=100")
        expect(u.contains("%26count%3D100"), "escaped query")
    }
    test("decoder normalizes 15 minute accumulation into equivalent mm per hour") {
        val decoded = OpenMeteo.decode(fixture(), time)
        near(decoded.current.precipitationMmH!!, 4.0, "precip interval")
        near(decoded.current.rainMmH!!, 4.0, "rain + showers interval")
        near(decoded.current.windMs!!, 3.0, "wind already m/s")
        expect(decoded.current.probabilityPct == 70, "upcoming-hour probability")
        near(decoded.current.uv!!, 2.0, "most recent hourly UV, not today's maximum")
    }
    test("decoder never double-applies timezone offset to Unix time") {
        val f = fixture().toMutableMap(); f["utc_offset_seconds"] = 18000
        val decoded = OpenMeteo.decode(f, time)
        expect(decoded.current.at == time, "Unix timestamp already UTC")
    }
    test("missing interval does not assume an accumulation period") {
        val f = fixture().toMutableMap(); val c = (f["current"] as Map<*, *>).entries.associate { it.key as String to it.value }.toMutableMap()
        c.remove("interval"); f["current"] = c
        expect(OpenMeteo.decode(f, time).current.precipitationMmH == null, "unknown interval")
    }
    test("zero missing and invalid observations remain distinct") {
        val f = fixture().toMutableMap(); val c = (f["current"] as Map<*, *>).entries.associate { it.key as String to it.value }.toMutableMap()
        c["wind_speed_10m"] = 0.0; c["relative_humidity_2m"] = null; c["rain"] = -1.0; f["current"] = c
        val p = OpenMeteo.decode(f, time).current
        near(p.windMs!!, 0.0, "calm"); expect(p.humidityPct == null, "missing RH")
        expect(p.rainMmH == null, "bad amount")
    }
    test("malformed mandatory current temperature is rejected") {
        val f = fixture().toMutableMap(); f["current"] = mapOf("time" to time.epochSecond, "temperature_2m" to null)
        expect(runCatching { OpenMeteo.decode(f, time) }.isFailure, "no usable temperature")
    }
    test("unexpected API unit never silently gives wrong advice") {
        val f = fixture().toMutableMap(); f["current_units"] = mapOf("temperature_2m" to "°F", "time" to "unixtime")
        expect(runCatching { OpenMeteo.decode(f, time) }.isFailure, "unit mismatch")
    }
    test("short hourly arrays preserve nulls and do not crash") {
        val f = fixture().toMutableMap(); val h = (f["hourly"] as Map<*, *>).entries.associate { it.key as String to it.value }.toMutableMap()
        h["wind_speed_10m"] = emptyList<Double>(); f["hourly"] = h
        val d = OpenMeteo.decode(f, time)
        expect(d.hourly.isNotEmpty() && d.hourly.all { it.windMs == null }, "short vector")
    }
    test("empty city response is empty not a fake default town") {
        expect(OpenMeteo.cities(emptyMap()).isEmpty(), "empty geocoder")
        val rows = listOf(mapOf("name" to "Москва", "latitude" to 55.75, "longitude" to 37.62, "country" to "Россия", "admin1" to "Москва"), mapOf("name" to "Bad", "latitude" to 150, "longitude" to 0))
        val cities = OpenMeteo.cities(mapOf("results" to rows))
        expect(cities.size == 1 && cities.first().name == "Москва", "valid city only")
    }
    test("cached forecast is anchored to evaluation time not download time") {
        val rainAlreadyPassed = point().copy(at = time.plusSeconds(3600), code = 63, rainMmH = 2.0)
        val old = WeatherConditions(point(), listOf(rainAlreadyPassed), time, evaluationAt = time.plusSeconds(7200))
        expect(old.forecastWindow(3).isEmpty(), "past event is not future")
        expect(Reason.RAIN_LATER !in RecommendationEngine().recommend(old).reasons, "no stale future claim")
        expect(old.fetchedAt == time, "freshness timestamp is not rewritten")
    }
    test("freezing air cannot recommend warm-weather clothes from contradictory apparent data") {
        val r = result(point(-25.0).copy(apparentC = 12.0))
        expect(r.layers.any { it.garment == Garment.WINTER_COAT }, "actual severe cold must constrain wardrobe")
        expect(r.layers.any { it.garment == Garment.THERMAL_TOP }, "thermal base in severe cold")
    }
    test("extreme hot air cannot recommend winter clothes from contradictory apparent data") {
        val r = result(point(40.0).copy(apparentC = -5.0))
        expect(r.layers.none { it.garment == Garment.WINTER_COAT || it.garment == Garment.THERMAL_TOP }, "avoid dangerous insulation")
    }
    test("saturated humid heat still warns when provider apparent temperature is missing") {
        val r = result(point(33.0).copy(apparentC = null, humidityPct = 100))
        expect(r.warnings.any { it.hazard == Hazard.EXTREME_HEAT }, "bounded fallback must not hide humidity risk")
    }
    test("forecast window sorts deduplicates and discards past data") {
        val after = point().copy(at = time.plusSeconds(3600)); val before = point().copy(at = time.minusSeconds(3600))
        val w = WeatherConditions(point(), listOf(after, before, after), time)
        expect(w.forecastWindow(3) == listOf(after), "sorted unique future")
    }
}

/** Synthetic contract fixture, not a claim that live network was executed. */
fun fixture(): Map<String, Any?> {
    val t = CoreChecks.time.epochSecond
    val units = mapOf("time" to "unixtime", "temperature_2m" to "°C", "apparent_temperature" to "°C", "relative_humidity_2m" to "%",
        "wind_speed_10m" to "m/s", "wind_gusts_10m" to "m/s", "precipitation" to "mm", "rain" to "mm", "showers" to "mm", "snowfall" to "cm", "precipitation_probability" to "%", "cloud_cover" to "%")
    return mapOf("timezone" to "Asia/Yekaterinburg", "current_units" to units, "hourly_units" to units,
        "current" to mapOf("time" to t, "interval" to 900, "temperature_2m" to 10.0, "apparent_temperature" to 7.0,
            "relative_humidity_2m" to 80, "wind_speed_10m" to 3.0, "wind_gusts_10m" to 5.0, "precipitation" to 1.0,
            "rain" to 0.75, "showers" to 0.25, "snowfall" to 0.0, "weather_code" to 61, "is_day" to 1, "cloud_cover" to 70),
        "hourly" to mapOf("time" to listOf(t - 900, t + 2700, t + 6300), "temperature_2m" to listOf(10.0, 9.0, 8.0),
            "uv_index" to listOf(2.0, 4.0, 5.0), "precipitation_probability" to listOf(10, 70, 80)))
}
