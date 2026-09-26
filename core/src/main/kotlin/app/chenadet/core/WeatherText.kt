package app.chenadet.core

import java.util.Locale
import kotlin.math.roundToInt

data class MetricInfo(val title: String, val value: String, val explanation: String)

/** Russian presentation strings live here, not in the decision rules. */
object WeatherText {
    fun temperature(value: Double): String {
        val rounded = value.roundToInt()
        return "${if (rounded > 0) "+" else ""}$rounded°"
    }
    private fun decimal(value: Double): String = String.format(Locale.forLanguageTag("ru"), "%.1f", value)
    fun garment(value: Garment): String = when (value) {
        Garment.T_SHIRT -> "Футболка"; Garment.LONG_SLEEVE -> "Лонгслив"; Garment.THERMAL_TOP -> "Термобельё сверху"
        Garment.SWEATSHIRT -> "Худи или плотная кофта"; Garment.FLEECE -> "Флис или тёплая кофта"
        Garment.WINDBREAKER -> "Непродуваемая ветровка"; Garment.RAIN_JACKET -> "Лёгкая куртка от дождя с вентиляцией"
        Garment.LIGHT_JACKET -> "Лёгкая куртка"; Garment.INSULATED_JACKET -> "Утеплённая непродуваемая куртка"
        Garment.WINTER_COAT -> "Тёплая зимняя куртка"; Garment.LIGHT_BOTTOMS -> "Лёгкие брюки или шорты"
        Garment.TROUSERS -> "Брюки"; Garment.WARM_TROUSERS -> "Тёплые брюки"
        Garment.THERMAL_BOTTOMS -> "Тёплые брюки с термобельём"; Garment.AIRY_SHOES -> "Дышащая обувь"
        Garment.CLOSED_SHOES -> "Закрытая обувь"; Garment.WATERPROOF_SHOES -> "Непромокаемая обувь"
        Garment.WINTER_BOOTS -> "Тёплая влагостойкая обувь с цепкой подошвой"
    }
    fun accessory(value: Accessory): String = when (value) {
        Accessory.WARM_HAT -> "Тёплая шапка"; Accessory.GLOVES -> "Перчатки"; Accessory.SCARF -> "Шарф"
        Accessory.SUN_HAT -> "Головной убор от солнца"; Accessory.SUNGLASSES -> "Солнцезащитные очки"
        Accessory.SUNSCREEN -> "Солнцезащитный крем"; Accessory.WATER -> "Вода"; Accessory.UMBRELLA -> "Зонт"
        Accessory.PACKABLE_RAINCOAT -> "Дождевик"; Accessory.SPARE_LAYER -> "Дополнительный тёплый слой"
    }
    fun reason(value: Reason): String = when (value) {
        Reason.TEMPERATURE -> "Слои подобраны по температуре и ощущению погоды."
        Reason.FEELS_LIKE -> "Ощущение отличается от температуры воздуха; для утепления учитываем его."
        Reason.WIND -> "Ветер усиливает потерю тепла — полезен непродуваемый внешний слой."
        Reason.WET -> "Защита от дождя помогает одежде и обуви оставаться сухими."
        Reason.SNOW -> "Снег требует влагостойкой обуви и защиты от намокания."
        Reason.UV -> "UV от 3: нужны тень и защита кожи и глаз."
        Reason.HUMID_HEAT -> "В жарком влажном воздухе пот испаряется хуже — выбирайте свободную лёгкую одежду."
        Reason.COLD_SENSITIVE -> "Вы чаще мёрзнете: одежда подобрана немного теплее."
        Reason.HOT_SENSITIVE -> "Вам часто жарко: одежда подобрана немного легче."
        Reason.ACTIVITY -> "Для выбранного режима активности изменено утепление, но не показание «ощущается»."
        Reason.LONG_EXPOSURE -> "Долгое пребывание снаружи: небольшой запас утепления и прогноз на 6 часов."
        Reason.COOLING_LATER -> "В ближайшие часы может ощутимо похолодать — пригодится съёмный слой."
        Reason.RAIN_LATER -> "В ближайшие часы возможны осадки — защиту лучше взять с собой."
        Reason.UV_LATER -> "Позже ожидается UV от 3 — возьмите защиту от солнца."
        Reason.STRONG_WIND -> "При сильном ветре или порывах зонт ненадёжен; лучше защищающая от дождя одежда."
        Reason.TRANSITION -> "Пограничная температура: этот слой можно снять, если станет тепло."
        Reason.MISSING_DATA -> "Часть показателей недоступна. Неизвестное значение не означает штиль или отсутствие риска."
        Reason.SAFETY_LIMIT -> "При морозе или жаре одежда дополнительно ограничена фактической температурой, чтобы противоречивый показатель ощущения не дал опасный совет."
        Reason.APPARENT_ESTIMATED -> "Показатель ощущения отсутствует: использована ограниченная расчётная оценка или температура воздуха."
    }
    fun warning(value: WeatherWarning): String = (if (value.upcoming) "В ближайшие часы: " else "") + when (value.hazard) {
        Hazard.THUNDERSTORM -> "Возможна гроза. Перейдите в капитальное здание или закрытый автомобиль; не укрывайтесь под одиноким деревом."
        Hazard.DANGEROUS_WIND -> "Сильный ветер или опасные порывы. Сократите прогулку, держитесь подальше от деревьев и незакреплённых конструкций."
        Hazard.HEAVY_RAIN -> "Сильные осадки. Лучше переждать в укрытии; не заходите в затопленные участки."
        Hazard.FREEZING_PRECIPITATION -> "Возможны ледяные осадки и скользкие поверхности. По возможности отложите выход."
        Hazard.POSSIBLE_ICE -> "Мокро или снежно около нуля: поверхности могут быть скользкими. Это не измерение состояния дороги."
        Hazard.EXTREME_COLD -> "Сильный мороз. Ограничьте время снаружи, закрывайте кожу и регулярно согревайтесь в помещении."
        Hazard.EXTREME_HEAT -> "Сильная жара. Снизьте нагрузку, пейте воду, ищите тень и прохладное помещение."
        Hazard.VERY_HIGH_UV -> "Очень высокий UV. Ограничьте пребывание под прямым солнцем, особенно около полудня."
    }
    fun headline(r: Recommendation): String {
        val first = when (r.thermalState) {
            ThermalState.VERY_COLD -> "Сильный мороз"; ThermalState.FREEZING -> "Морозно"; ThermalState.COLD -> "Холодно"
            ThermalState.COOL -> "Прохладно"; ThermalState.FRESH -> "Свежо"; ThermalState.WARM -> "Тепло"; ThermalState.HOT -> "Жарко"
        }
        val second = when (r.modifier) {
            WeatherModifier.THUNDERSTORM -> "возможна гроза"; WeatherModifier.SNOWY -> "снежно"
            WeatherModifier.RAINY -> "дождливо"; WeatherModifier.WINDY -> "ветрено"; WeatherModifier.MUGGY -> "душно"
            WeatherModifier.SUNNY -> "солнечно"; WeatherModifier.CLOUDY -> "пасмурно"; null -> null
        }
        return if (second == null) first else "$first, $second"
    }
    fun outfit(r: Recommendation): String = r.layers.filter { it.garment.slot in setOf(Slot.BASE, Slot.MID, Slot.OUTER) }
        .joinToString(" + ") { garment(it.garment) + if (it.optional) " по желанию" else "" }
    fun sensitivity(value: Sensitivity): String = when (value) { Sensitivity.COLD -> "Часто мёрзну"; Sensitivity.NORMAL -> "Обычно"; Sensitivity.HOT -> "Часто жарко" }
    fun activity(value: Activity): String = when (value) { Activity.INDOORS -> "Помещение / машина"; Activity.CITY -> "Прогулка по городу"; Activity.OUTDOORS -> "Долго на улице"; Activity.ACTIVE -> "Активное движение" }
    fun freshness(value: Freshness): String = when (value) {
        Freshness.CURRENT -> "Недавно обновлено"
        Freshness.STALE -> "Сохранённые данные: совет может не учитывать изменения погоды."
        Freshness.EXPIRED -> "Прогноз старше 6 часов. Совет по одежде скрыт до обновления."
        Freshness.HIDDEN -> "Нет свежих данных. Проверьте интернет и правильность времени на телефоне."
    }
    fun apparentExplanation(p: WeatherPoint, r: Recommendation): String {
        val base = when (r.apparentSource) {
            ApparentSource.PROVIDER -> "Расчёт сервиса с учётом ветра, влажности и солнечного излучения, а не отдельное измерение."
            ApparentSource.WIND_CHILL -> "Оценка охлаждения ветром для холодного воздуха. Солнце и ваши особенности формула не учитывает."
            ApparentSource.HEAT_INDEX -> "Оценка жары по температуре и влажности в тени. Солнечное излучение и активность отдельно не учтены."
            ApparentSource.AIR_TEMPERATURE -> "Недостаточно данных для надёжной оценки. Здесь температура воздуха, а не измеренное ощущение."
        }
        val factor = when {
            r.apparentC < p.temperatureC - 1 && p.isWindy() -> " Ветер может усиливать чувство холода."
            p.temperatureC >= 25 && (p.humidityPct ?: 0) >= 70 -> " Высокая влажность затрудняет охлаждение испарением."
            else -> ""
        }
        return base + factor + " Личные настройки меняют совет по одежде, а не эту цифру."
    }
    fun metrics(point: WeatherPoint, recommendation: Recommendation, evaluationAt: java.time.Instant = point.at): List<MetricInfo> {
        val p = point.normalized()
        fun metric(label: String, value: Double?, suffix: String, explanation: String) = MetricInfo(label,
            value?.let { decimal(it) + suffix } ?: "Нет данных", if (value == null) "Сервис не передал показатель. Отсутствие данных не равно нулю." else explanation)
        return listOf(
            MetricInfo("Температура", temperature(p.temperatureC), "Температура воздуха по погодной модели. Ветер, мокрая одежда, солнце и ваша активность меняют ощущения."),
            MetricInfo("Ощущается", "≈ ${temperature(recommendation.apparentC)}", apparentExplanation(p, recommendation)),
            metric("Ветер", p.windMs, " м/с", when { (p.windMs ?: 0.0) >= 10 -> "Сильный ветер. Нужна непродуваемая одежда; зонт лучше не использовать."; (p.windMs ?: 0.0) >= 5 -> "Заметный ветер усиливает потерю тепла. Полезна непродуваемая оболочка."; else -> "Слабый ветер. Специальная защита только от него обычно не нужна." }),
            metric("Порывы", p.gustMs, " м/с", "Кратковременные усиления ветра. ${if ((p.gustMs ?: 0.0) >= 14) "Порывы сильные: зонт может быть ненадёжен." else "Показатель дополняет среднюю скорость при выборе защиты."}"),
            metric("Влажность", p.humidityPct?.toDouble(), "%", when { p.temperatureC >= 25 && (p.humidityPct ?: 0) >= 70 -> "Высокая влажность в жару: пот испаряется хуже, поэтому важна лёгкая свободная одежда."; (p.humidityPct ?: 0) >= 80 -> "Высокая влажность. В холодном воздухе она сама по себе не означает большой дополнительный холод; важнее ветер и намокание."; (p.humidityPct ?: 0) <= 30 -> "Воздух сухой. Это не повод автоматически уменьшать утепление."; else -> "Умеренная влажность. Не требует самостоятельного дополнительного слоя одежды." }),
            metric("Вероятность осадков", p.upcomingProbability(evaluationAt)?.toDouble(), "%", "Прогноз на текущий незавершённый час. Это шанс осадков, а не их сила или доля времени. От 40% предлагаем взять защиту."),
            metric("Осадки", p.precipitationMmH, " мм/ч", "Эквивалентная часовая интенсивность недавнего интервала, включая снег в водном эквиваленте. Это не обещание такой же интенсивности на следующий час. Для одежды важна защита от намокания."),
            metric("Снег", p.snowCmH, " см/ч", "Эквивалентная интенсивность свежего снегопада, не высота сугробов. Нужна подходящая влагостойкая обувь."),
            metric("UV", if (p.isDay == false) null else p.uv, "", when { (p.uv ?: 0.0) >= 8 -> "Очень высокий уровень: избегайте прямого солнца, защищайте кожу и глаза."; (p.uv ?: 0.0) >= 3 -> "Уровень требует защиты: тень, закрывающая кожу одежда, головной убор, очки и солнцезащитный крем."; else -> "Низкий уровень. Не означает, что в ближайшие часы он не вырастет." }).let { if (p.isDay == false) it.copy(value = "Ночь", explanation = "Сейчас ночь. Защиту от солнца предлагаем только при подходящем прогнозе на время вашей прогулки.") else it },
            metric("Облачность", p.cloudPct?.toDouble(), "%", "Доля неба, закрытая облаками. ${if ((p.cloudPct ?: 0) >= 85) "Преимущественно пасмурно." else if ((p.cloudPct ?: 100) <= 25) "Облаков мало." else "Переменная облачность."} UV оцениваем отдельно: облака не гарантируют защиту от солнца.")
        )
    }
}
