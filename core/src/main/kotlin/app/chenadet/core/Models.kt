package app.chenadet.core

import java.time.Instant

/** All measurements use SI-like display units: °C, m/s, mm/h, cm/h, percent. */
data class WeatherPoint(
    val at: Instant,
    val temperatureC: Double,
    val apparentC: Double? = null,
    val humidityPct: Int? = null,
    val windMs: Double? = null,
    val gustMs: Double? = null,
    val precipitationMmH: Double? = null,
    val rainMmH: Double? = null,
    val snowCmH: Double? = null,
    val probabilityPct: Int? = null,
    val code: Int? = null,
    val uv: Double? = null,
    val isDay: Boolean? = null,
    val cloudPct: Int? = null,
)

data class WeatherConditions(
    val current: WeatherPoint,
    val hourly: List<WeatherPoint> = emptyList(),
    val fetchedAt: Instant = current.at,
    val timezone: String = "UTC",
    val evaluationAt: Instant = fetchedAt,
)

enum class Sensitivity(val clothingOffsetC: Double) { COLD(-2.0), NORMAL(0.0), HOT(2.0) }
enum class Activity(val clothingOffsetC: Double, val horizonHours: Int) {
    INDOORS(1.0, 1), CITY(0.0, 3), OUTDOORS(-1.5, 6), ACTIVE(3.0, 3)
}
data class UserProfile(val sensitivity: Sensitivity = Sensitivity.NORMAL, val activity: Activity = Activity.CITY)

enum class LocationMode { DEVICE, MANUAL }
data class Place(val name: String, val latitude: Double, val longitude: Double, val region: String = "") {
    init {
        require(latitude.isFinite() && latitude in -90.0..90.0)
        require(longitude.isFinite() && longitude in -180.0..180.0)
        require(name.isNotBlank())
    }
    /** ~1 km weather-cell cache identity, not a claim about GPS accuracy. */
    val key: String get() = java.lang.String.format(java.util.Locale.ROOT, "%.2f,%.2f", latitude, longitude)
    val displayName: String get() = if (region.isBlank()) name else "$name · $region"
}

enum class ApparentSource { PROVIDER, WIND_CHILL, HEAT_INDEX, AIR_TEMPERATURE }
enum class ThermalState { VERY_COLD, FREEZING, COLD, COOL, FRESH, WARM, HOT }
enum class WeatherModifier { THUNDERSTORM, SNOWY, RAINY, WINDY, MUGGY, SUNNY, CLOUDY }
enum class Slot { BASE, MID, OUTER, LEGS, FEET }
enum class Garment(val slot: Slot) {
    T_SHIRT(Slot.BASE), LONG_SLEEVE(Slot.BASE), THERMAL_TOP(Slot.BASE),
    SWEATSHIRT(Slot.MID), FLEECE(Slot.MID),
    WINDBREAKER(Slot.OUTER), RAIN_JACKET(Slot.OUTER), LIGHT_JACKET(Slot.OUTER),
    INSULATED_JACKET(Slot.OUTER), WINTER_COAT(Slot.OUTER),
    LIGHT_BOTTOMS(Slot.LEGS), TROUSERS(Slot.LEGS), WARM_TROUSERS(Slot.LEGS), THERMAL_BOTTOMS(Slot.LEGS),
    AIRY_SHOES(Slot.FEET), CLOSED_SHOES(Slot.FEET), WATERPROOF_SHOES(Slot.FEET), WINTER_BOOTS(Slot.FEET)
}
enum class Accessory { WARM_HAT, GLOVES, SCARF, SUN_HAT, SUNGLASSES, SUNSCREEN, WATER, UMBRELLA, PACKABLE_RAINCOAT, SPARE_LAYER }
enum class Reason {
    TEMPERATURE, FEELS_LIKE, WIND, WET, SNOW, UV, HUMID_HEAT,
    COLD_SENSITIVE, HOT_SENSITIVE, ACTIVITY, LONG_EXPOSURE,
    COOLING_LATER, RAIN_LATER, UV_LATER, STRONG_WIND, TRANSITION,
    MISSING_DATA, APPARENT_ESTIMATED, SAFETY_LIMIT
}
enum class Hazard { THUNDERSTORM, DANGEROUS_WIND, HEAVY_RAIN, FREEZING_PRECIPITATION, POSSIBLE_ICE, EXTREME_COLD, EXTREME_HEAT, VERY_HIGH_UV }
enum class Severity { CAUTION, DANGER }
data class WeatherWarning(val hazard: Hazard, val severity: Severity, val upcoming: Boolean = false)
data class ClothingLayer(val garment: Garment, val reasons: Set<Reason>, val optional: Boolean = false)
data class AccessoryAdvice(val accessory: Accessory, val reasons: Set<Reason>)
data class Recommendation(
    val apparentC: Double,
    val apparentSource: ApparentSource,
    val clothingTemperatureC: Double,
    val warmthIndex: Double,
    val thermalState: ThermalState,
    val modifier: WeatherModifier?,
    val layers: List<ClothingLayer>,
    val accessories: List<AccessoryAdvice>,
    val warnings: List<WeatherWarning>,
    val reasons: Set<Reason>,
    val horizonHours: Int,
)
