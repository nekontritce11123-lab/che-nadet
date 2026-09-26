package app.chenadet.core

import kotlin.math.pow

fun Double?.valid(min: Double, max: Double): Double? = this?.takeIf { it.isFinite() && it in min..max }
fun Int?.percent(): Int? = this?.takeIf { it in 0..100 }

/** Invalid optional readings remain missing. Do not turn them into calm/clear weather. */
fun WeatherPoint.normalized(): WeatherPoint = copy(
    apparentC = apparentC.valid(-100.0, 85.0), humidityPct = humidityPct.percent(),
    windMs = windMs.valid(0.0, 120.0), gustMs = gustMs.valid(0.0, 150.0),
    precipitationMmH = precipitationMmH.valid(0.0, 500.0), rainMmH = rainMmH.valid(0.0, 500.0),
    snowCmH = snowCmH.valid(0.0, 100.0), probabilityPct = probabilityPct.percent(),
    uv = uv.valid(0.0, 40.0), cloudPct = cloudPct.percent(), code = code?.takeIf { it in WeatherCodes.known },
)

object WeatherCodes {
    val rain = setOf(51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82, 95, 96, 99)
    val snow = setOf(71, 73, 75, 77, 85, 86)
    val freezing = setOf(56, 57, 66, 67)
    val storm = setOf(95, 96, 99)
    val known = setOf(0, 1, 2, 3, 45, 48) + rain + snow
}

fun WeatherPoint.isRain(): Boolean = (rainMmH ?: 0.0) > 0.05 || code in WeatherCodes.rain ||
    ((precipitationMmH ?: 0.0) > 0.05 && !isSnow())
fun WeatherPoint.isSnow(): Boolean = (snowCmH ?: 0.0) > 0.0 || code in WeatherCodes.snow
fun WeatherPoint.isWindy(): Boolean = (windMs ?: 0.0) >= 5.0 || (gustMs ?: 0.0) >= 9.0

/** No extrapolation outside the domains below; fallback is explicitly labelled in presentation. */
fun apparentTemperature(point: WeatherPoint): Pair<Double, ApparentSource> {
    val p = point.normalized()
    require(p.temperatureC.isFinite() && p.temperatureC in -100.0..65.0) { "Missing or invalid air temperature" }
    p.apparentC?.let { return it to ApparentSource.PROVIDER }
    val t = p.temperatureC
    val wind = p.windMs
    if (t <= 10.0 && wind != null && wind > 1.34) {
        val v = (wind * 3.6).pow(0.16)
        return (13.12 + 0.6215 * t - 11.37 * v + 0.3965 * t * v) to ApparentSource.WIND_CHILL
    }
    val rh = p.humidityPct?.toDouble()
    // Restricted Rothfusz regression region; not a full universal heat-stress calculation.
    if (t in 26.7..44.4 && rh != null && rh in 40.0..85.0) {
        val f = t * 9.0 / 5.0 + 32.0
        val indexF = -42.379 + 2.04901523 * f + 10.14333127 * rh - 0.22475541 * f * rh -
            0.00683783 * f * f - 0.05481717 * rh * rh + 0.00122874 * f * f * rh +
            0.00085282 * f * rh * rh - 0.00000199 * f * f * rh * rh
        return ((indexF - 32.0) * 5.0 / 9.0) to ApparentSource.HEAT_INDEX
    }
    return t to ApparentSource.AIR_TEMPERATURE
}

fun WeatherConditions.forecastWindow(hours: Int): List<WeatherPoint> {
    require(hours in 1..24)
    val end = evaluationAt.plusSeconds(hours * 3600L)
    return hourly.filter { it.at > evaluationAt && it.at <= end && it.temperatureC.isFinite() && it.temperatureC in -100.0..65.0 }
        .sortedBy { it.at }.distinctBy { it.at }.map { it.normalized() }
}

/** A current-point probability is a forecast with an expiry, never an indefinitely live reading. */
fun WeatherPoint.upcomingProbability(evaluationAt: java.time.Instant): Int? {
    val end = probabilityUntil ?: at.plusSeconds(3600)
    return probabilityPct.percent()?.takeIf { evaluationAt < end && evaluationAt >= at.minusSeconds(300) }
}
