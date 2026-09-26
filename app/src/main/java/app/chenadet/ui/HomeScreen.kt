package app.chenadet.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.chenadet.core.*
import app.chenadet.presentation.HomeState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class HomeActions(
    val refresh: () -> Unit = {}, val search: (String) -> Unit = {}, val chooseCity: (Place) -> Unit = {},
    val closeSearch: () -> Unit = {}, val setProfile: (UserProfile) -> Unit = {}, val useDevice: () -> Unit = {},
    val requestPermission: () -> Unit = {}, val openAppSettings: () -> Unit = {},
    val openLocationSettings: () -> Unit = {}, val dismissNotice: () -> Unit = {},
)
data class InfoContent(val title: String, val body: String)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(state: HomeState, actions: HomeActions) {
    var sheet by rememberSaveable { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<InfoContent?>(null) }
    val weather = state.visibleWeather
    val advice = state.recommendation
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        if (!state.initialized) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Чё надеть?", style = MaterialTheme.typography.headlineLarge)
                    CircularProgressIndicator()
                    Text("Открываем ваши настройки")
                }
            }
        } else LazyColumn(
            Modifier.fillMaxSize().padding(padding).testTag("home"),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "header") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Чё надеть?", Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        FilledTonalButton(onClick = { sheet = "cities" }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(state.place?.name ?: "Выбрать город")
                        }
                        TextButton(onClick = { sheet = "settings" }) { Text("Настроить") }
                        TextButton(onClick = actions.refresh, enabled = !state.loading && state.place != null) { Text("Обновить") }
                    }
                    if (!state.place?.region.isNullOrBlank()) Text(state.place!!.region, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (state.mode == LocationMode.MANUAL) Text("Город выбран вручную", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (state.mode == LocationMode.DEVICE && state.permission != PermissionStatus.GRANTED) item(key = "permission") {
                PermissionCard(state.permission, actions, onManual = { sheet = "cities" })
            }
            if (state.notice != null) item(key = "notice") {
                NoteCard(state.notice) { TextButton(onClick = actions.dismissNotice) { Text("Понятно") } }
            }
            if (state.error != null) item(key = "error") {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(state.error, color = MaterialTheme.colorScheme.onErrorContainer)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = actions.refresh, enabled = !state.loading) { Text("Повторить") }
                            TextButton(onClick = { sheet = "cities" }) { Text("Выбрать город вручную") }
                            if (state.locationDisabled) TextButton(onClick = actions.openLocationSettings) { Text("Включить геолокацию") }
                            else if (state.mode == LocationMode.DEVICE) TextButton(onClick = actions.openAppSettings) { Text("Настройки доступа") }
                        }
                    }
                }
            }
            if (state.loading) item(key = "loading") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(if (state.locating) "Определяем примерное местоположение…" else "Обновляем погоду…", style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (weather != null) {
                if (state.freshness != Freshness.CURRENT) item(key = "stale") { NoteCard(WeatherText.freshness(state.freshness)) }
                item(key = "hero") {
                    WeatherHero(weather, advice, onInfo = { info = it })
                }
                if (advice != null) {
                    if (advice.warnings.isNotEmpty()) item(key = "warnings") {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Heading(if (advice.warnings.any { it.severity == Severity.DANGER }) "Сначала — безопасность" else "Обратите внимание")
                                advice.warnings.take(2).forEach { Text(WeatherText.warning(it), color = MaterialTheme.colorScheme.onErrorContainer) }
                                if (advice.warnings.size > 2) TextButton(onClick = { info = InfoContent("Все предупреждения", advice.warnings.joinToString("\n\n") { WeatherText.warning(it) }) }) { Text("Все предупреждения (${advice.warnings.size})") }
                            }
                        }
                    }
                    item(key = "outfit") {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Heading("Что надеть")
                                Text(WeatherText.outfit(advice), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium)
                                Text(advice.layers.filter { it.garment.slot == Slot.LEGS || it.garment.slot == Slot.FEET }.joinToString(" · ") { WeatherText.garment(it.garment) },
                                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (Reason.COOLING_LATER in advice.reasons) Text("Позже похолодает — возьмите съёмный тёплый слой.", color = MaterialTheme.colorScheme.primary)
                                TextButton(onClick = { info = InfoContent("Слои и причины", advice.layers.joinToString("\n\n") {
                                    WeatherText.garment(it.garment) + (if (it.optional) " — по желанию" else "") + "\n" + it.reasons.joinToString(" ") { reason -> WeatherText.reason(reason) }
                                }) }) { Text("Подробнее об одежде") }
                            }
                        }
                    }
                    if (advice.accessories.isNotEmpty()) item(key = "accessories") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Heading("Взять с собой")
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                advice.accessories.take(4).forEach { extra ->
                                    FilledTonalButton(onClick = { info = InfoContent(WeatherText.accessory(extra.accessory), extra.reasons.joinToString("\n\n") { WeatherText.reason(it) }) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(WeatherText.accessory(extra.accessory)) }
                                }
                                if (advice.accessories.size > 4) OutlinedButton(onClick = { info = InfoContent("Что ещё пригодится", advice.accessories.drop(4).joinToString("\n\n") { WeatherText.accessory(it.accessory) + "\n" + it.reasons.joinToString(" ") { r -> WeatherText.reason(r) } }) }) { Text("Ещё ${advice.accessories.size - 4}") }
                            }
                        }
                    }
                    item(key = "why") {
                        OutlinedButton(onClick = { info = InfoContent("Почему такой совет", advice.reasons.joinToString("\n\n") { WeatherText.reason(it) } + "\n\nПороги одежды — ориентир, не медицинская гарантия. Сверяйтесь со своими ощущениями и официальными предупреждениями.") }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Почему такой совет?") }
                    }
                    item(key = "profile") {
                        TextButton(onClick = { sheet = "settings" }, modifier = Modifier.fillMaxWidth()) {
                            Text("${WeatherText.sensitivity(state.profile.sensitivity)} · ${WeatherText.activity(state.profile.activity)}", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    item(key = "metrics") {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Heading("Погода в деталях")
                            Text("Нажмите на показатель — объясним, что он меняет.", style = MaterialTheme.typography.bodyMedium)
                            MetricsGrid(WeatherText.metrics(weather.current, advice, state.now)) { info = InfoContent(it.title, it.explanation) }
                        }
                    }
                    item(key = "forecast") {
                        ForecastCard(weather.copy(evaluationAt = state.now), state.profile.activity.horizonHours)
                    }
                } else item(key = "expired") {
                    NoteCard("Старые цифры не подходят для нового совета по одежде.") {
                        Button(onClick = actions.refresh, enabled = !state.loading) { Text("Получить актуальную погоду") }
                    }
                }
            } else if (!state.loading && !(state.mode == LocationMode.DEVICE && state.permission != PermissionStatus.GRANTED)) item(key = "empty") {
                Card {
                    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Heading("Сначала узнаем, где вы")
                        Text(if (state.place == null) "Выберите город или разрешите примерное местоположение. Никакого слежения в фоне." else "Свежая погода пока недоступна. Проверьте интернет; слишком старые данные не выдаём за текущие.")
                        Button(onClick = { sheet = "cities" }) { Text("Выбрать город вручную") }
                        TextButton(onClick = actions.useDevice) { Text("По местоположению") }
                    }
                }
            }
            item(key = "attribution") {
                HorizontalDivider()
                TextButton(onClick = { sheet = "about" }, modifier = Modifier.fillMaxWidth()) {
                    Text("Open-Meteo · GeoNames · CC BY 4.0\nИсточники и конфиденциальность", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
    when (sheet) {
        "cities" -> CitySheet(state, actions, onClose = { actions.closeSearch(); sheet = null }, onChoose = { actions.chooseCity(it); sheet = null })
        "settings" -> SettingsSheet(state.profile, actions.setProfile, onDevice = { sheet = null; actions.useDevice() }, onClose = { sheet = null })
        "about" -> AboutSheet(onClose = { sheet = null })
    }
    info?.let { InfoDialog(it) { info = null } }
}

@Composable
internal fun Heading(text: String) { Text(text, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }

@Composable
private fun NoteCard(text: String, actions: @Composable ColumnScope.() -> Unit = {}) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text, color = MaterialTheme.colorScheme.onSecondaryContainer); actions()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PermissionCard(permission: PermissionStatus, actions: HomeActions, onManual: () -> Unit) {
    Card {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Heading("Погода рядом с вами")
            Text("Нужно только примерное местоположение, пока приложение открыто. Округлённые координаты передаются погодному сервису. Постоянного отслеживания нет.")
            if (permission == PermissionStatus.DENIED) Text("Можно продолжить без разрешения — просто выберите город.")
            if (permission == PermissionStatus.PERMANENTLY_DENIED) Text("Android больше не показывает запрос доступа. Разрешение можно изменить в настройках приложения.")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = if (permission == PermissionStatus.PERMANENTLY_DENIED) actions.openAppSettings else actions.requestPermission) {
                    Text(if (permission == PermissionStatus.PERMANENTLY_DENIED) "Открыть настройки" else "Разрешить местоположение")
                }
                TextButton(onClick = onManual) { Text("Выбрать город вручную") }
            }
        }
    }
}

@Composable
private fun WeatherHero(weather: WeatherConditions, advice: Recommendation?, onInfo: (InfoContent) -> Unit) {
    val p = weather.current
    val largeText = LocalDensity.current.fontScale > 1.3f
    val temperatureSize = if (WeatherText.temperature(p.temperatureC).length > 4) 44.sp else 56.sp
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(WeatherText.temperature(p.temperatureC), Modifier.weight(1f).clickable(role = Role.Button) {
                    onInfo(InfoContent("Температура воздуха", "Значение по погодной модели на ${localTime(p.at, weather.timezone)}. Ветер, влажность, солнце и ваша активность меняют ощущение погоды."))
                }.padding(vertical = 6.dp), fontSize = temperatureSize, lineHeight = temperatureSize * 1.15f, fontWeight = FontWeight.Medium)
                if (!largeText) WeatherGlyph(advice?.modifier, p.isDay == false)
            }
            if (advice != null) {
                Text(WeatherText.headline(advice), style = MaterialTheme.typography.headlineSmall)
                TextButton(onClick = { onInfo(InfoContent("Почему так ощущается", WeatherText.apparentExplanation(p, advice))) }) {
                    Text("Ощущается ≈ ${WeatherText.temperature(advice.apparentC)} · почему?", style = MaterialTheme.typography.titleMedium)
                }
            } else Text("Сохранённые условия", style = MaterialTheme.typography.titleMedium)
            Text("Условия на ${localTime(p.at, weather.timezone)}\nЗагружено ${localTime(weather.fetchedAt, weather.timezone)} · время города", style = MaterialTheme.typography.labelMedium)
            Text("Погодная модель, не замер за окном", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MetricsGrid(metrics: List<MetricInfo>, onClick: (MetricInfo) -> Unit) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (fontScale > 1.3f || maxWidth < 300.dp) 1 else 2
        val width = if (columns == 1) maxWidth else (maxWidth - 12.dp) / 2
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp), maxItemsInEachRow = columns) {
            metrics.forEach { metric ->
                OutlinedCard(onClick = { onClick(metric) }, modifier = Modifier.width(width).heightIn(min = 86.dp)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(metric.title, style = MaterialTheme.typography.labelLarge)
                        Text(metric.value, style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun ForecastCard(weather: WeatherConditions, horizon: Int) {
    val hours = weather.forecastWindow(horizon)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Heading("Ближайшие часы")
        Text("Горизонт для вашего режима: $horizon ч", style = MaterialTheme.typography.bodyMedium)
        if (hours.isEmpty()) Text("Почасовой прогноз недоступен. Не можем оценить, как изменится погода.")
        else hours.forEach { p ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${localTime(p.at, weather.timezone)}  ·  ${WeatherText.temperature(p.temperatureC)}", style = MaterialTheme.typography.titleMedium)
                    Text("Ощущается ≈ ${WeatherText.temperature(apparentTemperature(p).first)}" +
                        (p.probabilityPct?.let { "  ·  Осадки $it% за час к этому времени" } ?: "  ·  Вероятность осадков неизвестна"), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

internal fun localTime(instant: Instant, zone: String): String = DateTimeFormatter.ofPattern("HH:mm")
    .withZone(runCatching { ZoneId.of(zone) }.getOrDefault(ZoneId.of("UTC"))).format(instant)
