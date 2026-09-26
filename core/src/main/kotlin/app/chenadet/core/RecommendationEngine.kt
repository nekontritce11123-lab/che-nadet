package app.chenadet.core

import kotlin.math.abs

/** Pure deterministic clothing heuristics. Safety rules never use personal warmth adjustments. */
class RecommendationEngine {
    fun recommend(weather: WeatherConditions, profile: UserProfile = UserProfile()): Recommendation {
        val p = weather.current.normalized()
        val (apparent, source) = apparentTemperature(p)
        val future = weather.forecastWindow(profile.activity.horizonHours)
        val reasons = linkedSetOf(Reason.TEMPERATURE)
        if (abs(apparent - p.temperatureC) >= 1.0) reasons += Reason.FEELS_LIKE
        if (source != ApparentSource.PROVIDER) reasons += Reason.APPARENT_ESTIMATED
        if (listOf(p.humidityPct, p.windMs, p.gustMs, p.precipitationMmH, p.probabilityPct, p.uv, p.code).any { it == null }) reasons += Reason.MISSING_DATA
        if (profile.sensitivity == Sensitivity.COLD) reasons += Reason.COLD_SENSITIVE
        if (profile.sensitivity == Sensitivity.HOT) reasons += Reason.HOT_SENSITIVE
        if (profile.activity == Activity.ACTIVE || profile.activity == Activity.INDOORS) reasons += Reason.ACTIVITY
        if (profile.activity == Activity.OUTDOORS) reasons += Reason.LONG_EXPOSURE

        val effective = apparent + profile.sensitivity.clothingOffsetC + profile.activity.clothingOffsetC
        // Continuous score; clothing is necessarily discrete, with removable optional layers near thresholds.
        val warmth = ((24.0 - effective) / 9.0).coerceIn(0.0, 7.0)
        val wet = p.isRain()
        val snowy = p.isSnow()
        val windy = p.isWindy()
        if (wet) reasons += Reason.WET
        if (snowy) reasons += Reason.SNOW
        if (windy) reasons += Reason.WIND
        if (p.temperatureC >= 25 && (p.humidityPct ?: 0) >= 70) reasons += Reason.HUMID_HEAT

        val layers = mutableListOf<ClothingLayer>()
        fun layer(garment: Garment, why: Set<Reason> = setOf(Reason.TEMPERATURE), optional: Boolean = false) {
            layers += ClothingLayer(garment, why, optional)
        }
        layer(when { effective < 0 -> Garment.THERMAL_TOP; effective < 16 -> Garment.LONG_SLEEVE; else -> Garment.T_SHIRT })
        when {
            effective < 12 -> layer(Garment.FLEECE)
            effective < 20 -> layer(Garment.SWEATSHIRT, if (effective >= 17) setOf(Reason.TRANSITION) else setOf(Reason.TEMPERATURE), effective >= 17)
        }
        val outerReasons = linkedSetOf(Reason.TEMPERATURE)
        if (windy) outerReasons += Reason.WIND
        if (wet) outerReasons += Reason.WET
        if (snowy) outerReasons += Reason.SNOW
        val outer = when {
            effective < -5 -> Garment.WINTER_COAT
            effective < 7 -> Garment.INSULATED_JACKET
            wet -> Garment.RAIN_JACKET
            windy && effective < 28 -> Garment.WINDBREAKER
            effective < 13 -> Garment.LIGHT_JACKET
            else -> null
        }
        outer?.let { layer(it, outerReasons, effective in 11.0..12.999 && !wet && !windy) }
        layer(when { effective < -10 -> Garment.THERMAL_BOTTOMS; effective < 3 -> Garment.WARM_TROUSERS; effective < 24 -> Garment.TROUSERS; else -> Garment.LIGHT_BOTTOMS })
        layer(when { effective < -3 || snowy -> Garment.WINTER_BOOTS; wet -> Garment.WATERPROOF_SHOES; effective < 23 -> Garment.CLOSED_SHOES; else -> Garment.AIRY_SHOES },
            when { wet -> setOf(Reason.WET); snowy -> setOf(Reason.SNOW); else -> setOf(Reason.TEMPERATURE) })

        val extra = linkedMapOf<Accessory, MutableSet<Reason>>()
        fun add(item: Accessory, reason: Reason) { extra.getOrPut(item) { linkedSetOf() } += reason; reasons += reason }
        if (effective < 8) add(Accessory.WARM_HAT, Reason.TEMPERATURE)
        if (effective < 3) add(Accessory.GLOVES, Reason.TEMPERATURE)
        if (effective < 0) add(Accessory.SCARF, Reason.TEMPERATURE)
        if (apparent >= 25 || p.temperatureC >= 25 || (profile.activity == Activity.ACTIVE && p.temperatureC >= 18)) add(Accessory.WATER, Reason.TEMPERATURE)

        val all = listOf(p) + future
        val storm = all.any { it.code in WeatherCodes.storm }
        val strongWind = all.any { (it.windMs ?: 0.0) >= 10 || (it.gustMs ?: 0.0) >= 14 }
        // Missing wind is not permission to recommend an umbrella. Current wind must be known.
        val umbrellaOkay = !storm && !strongWind && p.windMs != null && p.gustMs != null &&
            future.all { it.windMs != null && it.gustMs != null }
        if (strongWind) reasons += Reason.STRONG_WIND
        val rainLater = future.any { it.isRain() || (it.probabilityPct ?: 0) >= 40 } || (!wet && (p.probabilityPct ?: 0) >= 40)
        if (wet && umbrellaOkay) add(Accessory.UMBRELLA, Reason.WET)
        if (wet && effective < 7) add(Accessory.PACKABLE_RAINCOAT, Reason.WET)
        if (rainLater) add(if (umbrellaOkay) Accessory.UMBRELLA else Accessory.PACKABLE_RAINCOAT, Reason.RAIN_LATER)
        val laterMin = future.minOfOrNull { apparentTemperature(it).first }
        if (laterMin != null && apparent - laterMin >= 4) add(Accessory.SPARE_LAYER, Reason.COOLING_LATER)
        val uvNow = p.isDay != false && (p.uv ?: 0.0) >= 3
        val uvLater = future.any { it.isDay != false && (it.uv ?: 0.0) >= 3 }
        if (uvNow || uvLater) {
            val why = if (uvNow) Reason.UV else Reason.UV_LATER
            add(Accessory.SUNGLASSES, why); add(Accessory.SUNSCREEN, why)
            if (effective >= 8) add(Accessory.SUN_HAT, why)
        }

        val warnings = mutableListOf<WeatherWarning>()
        fun warn(h: Hazard, severity: Severity, later: Boolean) {
            if (warnings.none { it.hazard == h }) warnings += WeatherWarning(h, severity, later)
        }
        for ((index, sample) in all.withIndex()) {
            val feels = apparentTemperature(sample).first
            val later = index > 0
            if (sample.code in WeatherCodes.storm) warn(Hazard.THUNDERSTORM, Severity.DANGER, later)
            if ((sample.windMs ?: 0.0) >= 17 || (sample.gustMs ?: 0.0) >= 20) warn(Hazard.DANGEROUS_WIND, Severity.DANGER, later)
            if ((sample.precipitationMmH ?: sample.rainMmH ?: 0.0) >= 7.5 || sample.code == 65 || sample.code == 82) warn(Hazard.HEAVY_RAIN, Severity.CAUTION, later)
            if (sample.code in WeatherCodes.freezing) warn(Hazard.FREEZING_PRECIPITATION, Severity.DANGER, later)
            if (sample.temperatureC <= 2 && (sample.isRain() || sample.isSnow())) warn(Hazard.POSSIBLE_ICE, Severity.CAUTION, later)
            if (sample.temperatureC <= -20 || feels <= -28) warn(Hazard.EXTREME_COLD, Severity.DANGER, later)
            if (sample.temperatureC >= 35 || feels >= 38) warn(Hazard.EXTREME_HEAT, Severity.DANGER, later)
            if (sample.isDay != false && (sample.uv ?: 0.0) >= 8) warn(Hazard.VERY_HIGH_UV, Severity.CAUTION, later)
        }
        val thermal = when {
            apparent < -20 -> ThermalState.VERY_COLD; apparent < 0 -> ThermalState.FREEZING
            apparent < 7 -> ThermalState.COLD; apparent < 14 -> ThermalState.COOL
            apparent < 20 -> ThermalState.FRESH; apparent < 28 -> ThermalState.WARM; else -> ThermalState.HOT
        }
        val modifier = when {
            p.code in WeatherCodes.storm -> WeatherModifier.THUNDERSTORM
            snowy -> WeatherModifier.SNOWY; wet -> WeatherModifier.RAINY; windy -> WeatherModifier.WINDY
            Reason.HUMID_HEAT in reasons -> WeatherModifier.MUGGY
            p.isDay == true && p.cloudPct != null && p.cloudPct <= 25 -> WeatherModifier.SUNNY
            p.cloudPct != null && p.cloudPct >= 85 -> WeatherModifier.CLOUDY
            else -> null
        }
        return Recommendation(apparent, source, effective, warmth, thermal, modifier, layers,
            extra.map { AccessoryAdvice(it.key, it.value) },
            warnings.sortedWith(compareBy<WeatherWarning> { if (it.severity == Severity.DANGER) 0 else 1 }.thenBy { it.hazard.ordinal }),
            reasons, profile.activity.horizonHours)
    }
}
