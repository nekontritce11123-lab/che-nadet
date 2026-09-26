package app.chenadet.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.chenadet.core.*
import app.chenadet.presentation.HomeState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CitySheet(state: HomeState, actions: HomeActions, onClose: () -> Unit, onChoose: (Place) -> Unit) {
    val focus = remember { FocusRequester() }
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.88f).imePadding().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Heading("Выберите город")
            OutlinedTextField(value = state.searchQuery, onValueChange = actions.search, singleLine = true,
                label = { Text("Название города") }, supportingText = { Text("Введите хотя бы 2 буквы. Город передаётся Open-Meteo / GeoNames.") },
                modifier = Modifier.fillMaxWidth().focusRequester(focus))
            if (state.searching) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (state.searchError != null) {
                Text(state.searchError, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { actions.search(state.searchQuery) }) { Text("Повторить поиск") }
            }
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.searchResults, key = { it.key }) { place ->
                    OutlinedCard(onClick = { onChoose(place) }, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(place.name, style = MaterialTheme.typography.titleMedium)
                            if (place.region.isNotBlank()) Text(place.region, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                if (state.searchQuery.trim().length >= 2 && !state.searching && state.searchError == null && state.searchResults.isEmpty()) item {
                    Text("Ничего не найдено. Проверьте написание или попробуйте ближайший крупный город.")
                }
            }
            TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Закрыть") }
        }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsSheet(profile: UserProfile, onProfile: (UserProfile) -> Unit, onDevice: () -> Unit, onClose: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Heading("Совет под вас")
            Text("Два выбора — без длинной анкеты. Они меняют одежду, но не температуру на экране.")
            Text("Как вы чувствуете температуру?", style = MaterialTheme.typography.titleMedium)
            Column(Modifier.selectableGroup()) {
                Sensitivity.values().forEach { value ->
                    ChoiceRow(WeatherText.sensitivity(value), profile.sensitivity == value) { onProfile(profile.copy(sensitivity = value)) }
                }
            }
            Text("Что планируете?", style = MaterialTheme.typography.titleMedium)
            Column(Modifier.selectableGroup()) {
                Activity.values().forEach { value ->
                    ChoiceRow("${WeatherText.activity(value)} · ${value.horizonHours} ч", profile.activity == value) { onProfile(profile.copy(activity = value)) }
                }
            }
            Text("Горизонт — сколько часов прогноза учитывается. Это не таймер и не обещание точности.", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = onDevice, modifier = Modifier.fillMaxWidth()) { Text("Использовать моё местоположение") }
            Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Готово") }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).selectable(selected = selected, role = Role.RadioButton, onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Text(label, Modifier.weight(1f))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AboutSheet(onClose: () -> Unit) {
    val uri = LocalUriHandler.current
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Heading("Просто понять, что надеть")
            Text("Чё надеть? · 0.1.0-preview\nДетерминированные правила. Без нейросетей, аккаунта, рекламы и аналитики.")
            Text("Погода — модель Open-Meteo, не измерение на вашей улице. Данные могут быть неполными или запаздывать. При опасной погоде сверяйтесь с официальными предупреждениями.")
            Text("Open-Meteo · данные CC BY 4.0. Поиск городов использует GeoNames. Бесплатный endpoint разрешён для некоммерческого использования; для рекламы или платного продукта нужны другие условия.")
            TextButton(onClick = { uri.openUri("https://open-meteo.com/") }) { Text("Open-Meteo: источник погоды") }
            TextButton(onClick = { uri.openUri("https://www.geonames.org/") }) { Text("GeoNames: названия городов") }
            TextButton(onClick = { uri.openUri("https://creativecommons.org/licenses/by/4.0/") }) { Text("Лицензия данных CC BY 4.0") }
            Heading("Ваши данные")
            Text("Разрешение на примерное местоположение используется только при открытом приложении. Перед запросом координаты округляются до 0,01°. Они передаются Open-Meteo; название города ищется через системный геокодер Android, когда он доступен. При ручном поиске отправляется введённое название города.")
            Text("На телефоне сохраняются профиль, выбранная локация и один последний погодный ответ. Облачный backup этих данных отключён. У приложения нет собственного сервера. У погодного сервиса есть свои журналы запросов и политика конфиденциальности.")
            TextButton(onClick = { uri.openUri("https://open-meteo.com/en/terms") }) { Text("Условия и конфиденциальность Open-Meteo") }
            Text("Удалить локальные данные или отозвать геолокацию можно в системных настройках приложения. Без разрешения всегда доступен ручной выбор города.")
            Heading("Откуда взялись правила")
            Text("Wind chill и heat index: National Weather Service. UV: Всемирная организация здравоохранения. Пороги одежды, чувствительность и поправки активности — наши эвристики; клиническая проверка не проводилась.")
            TextButton(onClick = { uri.openUri("https://www.weather.gov/safety/cold-wind-chill-chart") }) { Text("NWS: ветер и ощущение холода") }
            TextButton(onClick = { uri.openUri("https://www.who.int/news-room/questions-and-answers/item/radiation-the-ultraviolet-(uv)-index") }) { Text("ВОЗ: UV и защита от солнца") }
            Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Понятно") }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
internal fun InfoDialog(content: InfoContent, onClose: () -> Unit) {
    AlertDialog(onDismissRequest = onClose, title = { Text(content.title) },
        text = { Text(content.body, Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = onClose) { Text("Понятно") } })
}
