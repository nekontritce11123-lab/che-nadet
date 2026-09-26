package app.chenadet.core

import java.time.Instant
import java.io.File
import kotlin.math.abs

object CoreChecks {
    private val engine = RecommendationEngine()
    val time: Instant = Instant.parse("2026-09-26T10:15:00Z")
    var assertions = 0
    val passed = mutableListOf<String>()
    private val failures = mutableListOf<String>()
    fun expect(condition: Boolean, message: String) { assertions++; check(condition) { message } }
    fun near(a: Double, b: Double, message: String) = expect(abs(a - b) < 0.000001, "$message: $a != $b")
    fun point(t: Double = 15.0) = WeatherPoint(time, t, t, 50, 1.0, 2.0, 0.0, 0.0, 0.0, 0, 1, 1.0, true, 25)
    fun result(p: WeatherPoint, profile: UserProfile = UserProfile(), hours: List<WeatherPoint> = emptyList()) =
        engine.recommend(WeatherConditions(p, hours, time), profile)
    fun test(name: String, block: () -> Unit) {
        try { block(); passed += name; println("PASS $name") }
        catch (e: Exception) { failures += "$name: ${e.message}"; println("FAIL $name: ${e.message}") }
    }
    fun run(): Int {
        test("provider apparent temperature is not counted twice") {
            val r = result(point(10.0).copy(apparentC = 4.0, windMs = 9.0, gustMs = 12.0, humidityPct = 95))
            near(r.apparentC, 4.0, "provider apparent"); near(r.clothingTemperatureC, 4.0, "no extra wind/RH subtraction")
        }
        test("10 C calm wind and rain have different protective layers") {
            val calm = result(point(10.0)); val wind = result(point(10.0).copy(windMs = 8.0))
            val rain = result(point(10.0).copy(rainMmH = 2.0, precipitationMmH = 2.0, code = 63))
            expect(calm.layers != wind.layers, "wind protection must differ")
            expect(rain.layers.any { it.garment == Garment.RAIN_JACKET }, "rain shell")
            expect(rain.layers.any { it.garment == Garment.WATERPROOF_SHOES }, "rain footwear")
        }
        test("14.9 and 15 C do not jump from jacket to t-shirt") {
            expect(result(point(14.9)).layers.map { it.garment } == result(point(15.0)).layers.map { it.garment }, "boundary jump")
        }
        test("personalization changes clothes but not thermometer") {
            val cold = result(point(), UserProfile(Sensitivity.COLD)); val hot = result(point(), UserProfile(Sensitivity.HOT))
            near(cold.apparentC, hot.apparentC, "meteorological value")
            expect(cold.warmthIndex > hot.warmthIndex, "sensitivity affects warmth")
        }
        test("active movement lowers warmth; long exposure raises it") {
            val active = result(point(), UserProfile(activity = Activity.ACTIVE))
            val city = result(point()); val long = result(point(), UserProfile(activity = Activity.OUTDOORS))
            expect(active.warmthIndex < city.warmthIndex && city.warmthIndex < long.warmthIndex, "activity ordering")
        }
        test("strong wind with rain never recommends an umbrella") {
            val r = result(point().copy(rainMmH = 1.0, code = 61, windMs = 12.0, gustMs = 19.0))
            expect(r.accessories.none { it.accessory == Accessory.UMBRELLA }, "unsafe umbrella")
            expect(r.layers.any { it.garment == Garment.RAIN_JACKET }, "rain shell instead")
        }
        test("unknown wind chooses raincoat rather than umbrella") {
            val r = result(point().copy(rainMmH = 1.0, code = 61, windMs = null, gustMs = null))
            expect(r.accessories.none { it.accessory == Accessory.UMBRELLA }, "unknown wind")
            expect(Reason.MISSING_DATA in r.reasons, "missingness not silent")
        }
        test("thunderstorm warning outranks wardrobe") {
            val r = result(point(25.0).copy(code = 95))
            expect(r.warnings.firstOrNull()?.hazard == Hazard.THUNDERSTORM, "storm first")
            expect(r.accessories.none { it.accessory == Accessory.UMBRELLA }, "storm umbrella")
        }
        test("freezing rain differs from ordinary rain") {
            val r = result(point(-1.0).copy(code = 66, precipitationMmH = 1.0))
            expect(r.warnings.any { it.hazard == Hazard.FREEZING_PRECIPITATION }, "freezing rain")
        }
        test("cold wet conditions warn about possible slippery surfaces") {
            val r = result(point(1.0).copy(rainMmH = 1.0, code = 61))
            expect(r.warnings.any { it.hazard == Hazard.POSSIBLE_ICE }, "possible ice")
        }
        test("wind chill below zero alone does not imply icy roads") {
            val r = result(point(8.0).copy(apparentC = -1.0, windMs = 15.0))
            expect(r.warnings.none { it.hazard == Hazard.POSSIBLE_ICE }, "not an observed surface temp")
        }
        test("heat safety is independent from personal sensitivity") {
            for (s in Sensitivity.values()) {
                val r = result(point(39.0).copy(apparentC = 43.0, uv = 10.0), UserProfile(s))
                expect(r.warnings.any { it.hazard == Hazard.EXTREME_HEAT }, "heat warning $s")
                expect(r.accessories.any { it.accessory == Accessory.WATER }, "water $s")
            }
        }
        test("severe cold uses thermal layers hat and gloves") {
            val r = result(point(-30.0).copy(apparentC = -38.0))
            expect(r.warnings.any { it.hazard == Hazard.EXTREME_COLD }, "cold danger")
            expect(r.layers.any { it.garment == Garment.WINTER_COAT }, "winter coat")
            expect(r.layers.any { it.garment == Garment.THERMAL_TOP }, "thermal base")
            expect(r.accessories.any { it.accessory == Accessory.GLOVES }, "gloves")
        }
        test("daytime UV protection from 3 including cloudy weather") {
            val r = result(point(22.0).copy(uv = 3.0, cloudPct = 100))
            expect(r.accessories.any { it.accessory == Accessory.SUNSCREEN }, "UV not cloud coverage")
        }
        test("night never describes current sunshine or high current UV") {
            val r = result(point(22.0).copy(isDay = false, uv = 9.0, cloudPct = 0))
            expect(r.modifier != WeatherModifier.SUNNY, "night sunshine")
            expect(r.warnings.none { it.hazard == Hazard.VERY_HIGH_UV && !it.upcoming }, "night UV")
        }
        test("forecast rain packs protection without claiming rain now") {
            val later = point(14.0).copy(at = time.plusSeconds(3600), probabilityPct = 80, rainMmH = 2.0, code = 63)
            val r = result(point(), hours = listOf(later))
            expect(r.modifier != WeatherModifier.RAINY, "future is not present")
            expect(r.accessories.any { Reason.RAIN_LATER in it.reasons }, "pack protection")
        }
        test("rain outside activity window does not affect this outing") {
            val later = point().copy(at = time.plusSeconds(8 * 3600), probabilityPct = 100, code = 65)
            expect(Reason.RAIN_LATER !in result(point(), hours = listOf(later)).reasons, "8 hour forecast outside window")
        }
        test("fast cooling prompts an extra removable layer") {
            val later = point(5.0).copy(at = time.plusSeconds(3600))
            expect(result(point(18.0), hours = listOf(later)).accessories.any { it.accessory == Accessory.SPARE_LAYER }, "cooling layer")
        }
        test("missing apparent falls back to domain-limited wind chill") {
            val r = result(point(-10.0).copy(apparentC = null, windMs = 10.0))
            expect(r.apparentSource == ApparentSource.WIND_CHILL, "source")
            expect(r.apparentC < -15.0, "wind chill colder")
        }
        test("calm missing apparent does not invent cold humidity penalty") {
            val r = result(point(5.0).copy(apparentC = null, humidityPct = 95, windMs = 0.0))
            near(r.apparentC, 5.0, "no RH penalty")
            expect(r.apparentSource == ApparentSource.AIR_TEMPERATURE, "fallback transparent")
        }
        test("missing apparent has bounded heat index fallback") {
            val r = result(point(32.0).copy(apparentC = null, humidityPct = 70))
            expect(r.apparentSource == ApparentSource.HEAT_INDEX && r.apparentC > 35, "humid heat index")
        }
        test("nonfinite or out of range optional data is not treated as valid") {
            val r = result(point().copy(apparentC = Double.NaN, windMs = -3.0, humidityPct = 150, uv = Double.POSITIVE_INFINITY))
            expect(r.apparentC.isFinite() && r.warmthIndex.isFinite(), "finite output")
            expect(Reason.MISSING_DATA in r.reasons, "invalid input marked missing")
        }
        test("negative precipitation never creates a rain recommendation") {
            val r = result(point().copy(rainMmH = -1.0, precipitationMmH = -9.0))
            expect(r.modifier != WeatherModifier.RAINY, "negative rain")
            expect(Reason.MISSING_DATA in r.reasons, "invalid precip not zero")
        }
        test("warmth demand changes continuously across temperature grid") {
            var previous = result(point(-60.0)).warmthIndex
            for (step in -599..500) {
                val r = result(point(step / 10.0))
                expect(r.warmthIndex <= previous + 1e-9, "monotonic at $step")
                expect(abs(previous - r.warmthIndex) <= 0.02, "smooth at $step")
                previous = r.warmthIndex
            }
        }
        test("every profile and weather combination has sane unique layers") {
            for (t in listOf(-45.0, -25.0, -10.0, 0.0, 5.0, 10.0, 15.0, 20.0, 25.0, 33.0, 42.0)) {
                for (s in Sensitivity.values()) for (a in Activity.values()) for (wet in listOf(false, true)) {
                    val r = result(point(t).copy(rainMmH = if (wet) 2.0 else 0.0), UserProfile(s, a))
                    expect(r.layers.isNotEmpty(), "empty layers")
                    expect(r.layers.map { it.garment }.distinct().size == r.layers.size, "duplicate garment")
                    expect(r.layers.groupBy { it.garment.slot }.all { it.value.size == 1 }, "conflicting slot")
                    expect(r.layers.all { it.reasons.isNotEmpty() }, "unexplained layer")
                    expect(r.accessories.map { it.accessory }.distinct().size == r.accessories.size, "duplicate accessory")
                    expect(r.accessories.all { it.reasons.isNotEmpty() }, "unexplained accessory")
                    expect(!(r.accessories.any { it.accessory == Accessory.SUN_HAT } && r.accessories.any { it.accessory == Accessory.WARM_HAT }), "two hats")
                }
            }
        }
        if (failures.isNotEmpty()) {
            println("\nFAILED ${failures.size} groups / ${passed.size + failures.size}; assertions=$assertions")
            return 1
        }
        println("\nSUCCESS ${passed.size} groups; assertions=$assertions")
        return 0
    }
}

fun main(args: Array<String>) {
    val code = CoreChecks.run()
    if (args.isNotEmpty()) File(args[0]).writeText("# Executed core checks\n\nGroups passed: ${CoreChecks.passed.size}\nAssertions executed: ${CoreChecks.assertions}\n\n" + CoreChecks.passed.joinToString("\n") { "- PASS $it" } + "\n\nExit: $code\n")
    check(code == 0) { "Core checks failed" }
}
