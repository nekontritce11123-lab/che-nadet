package app.chenadet.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.chenadet.core.*
import app.chenadet.data.*
import app.chenadet.location.LocationFailure
import app.chenadet.location.LocationFailureReason
import java.time.Clock
import java.time.Duration
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class MainViewModel(private val repository: WeatherRepository, private val store: SettingsStore,
    private val location: LocationSource, private val clock: Clock = Clock.systemUTC()) : ViewModel() {
    private val mutableState = MutableStateFlow(HomeState(now = clock.instant()))
    val state: StateFlow<HomeState> = mutableState.asStateFlow()
    private val gate = RequestGate()
    private val searchGate = RequestGate()
    private var refreshJob: Job? = null
    private var searchJob: Job? = null
    private var foreground = false
    private var nativeGranted = false
    private var nativeRationale = false
    private var permissionAsked = false

    init {
        viewModelScope.launch {
            var notice: String? = null
            val saved = try { store.read() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { notice = "Настройки не прочитались. Пока используются стандартные."; SavedSettings() }
            permissionAsked = saved.permissionAsked && !nativeGranted
            if (saved.permissionAsked && nativeGranted) persist { store.savePermissionAsked(false) }
            val cache = saved.place?.let { try { repository.cached(it) }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { null } }
            mutableState.value = HomeState(initialized = true, place = saved.place, mode = saved.mode,
                profile = saved.profile, weather = cache, notice = notice, now = clock.instant(),
                permission = permissionStatus(nativeGranted, permissionAsked, nativeRationale))
            if (foreground) refresh(false)
        }
    }
    fun onForeground(granted: Boolean, showRationale: Boolean) {
        foreground = true; nativeGranted = granted; nativeRationale = showRationale
        if (granted && permissionAsked) { permissionAsked = false; persist { store.savePermissionAsked(false) } }
        mutableState.update { it.copy(permission = permissionStatus(granted, permissionAsked, showRationale), now = clock.instant()) }
        if (state.value.initialized) refresh(false)
    }
    fun onBackground() {
        foreground = false
        gate.next(); refreshJob?.cancel()
        searchGate.next(); searchJob?.cancel()
        mutableState.update { it.copy(loading = false, locating = false, searching = false, searchError = if (it.searching) "Поиск приостановлен. Повторите запрос." else it.searchError) }
    }
    fun tick() { mutableState.update { it.copy(now = clock.instant()) } }
    fun permissionRequested() { permissionAsked = true; persist { store.savePermissionAsked(true) } }
    fun permissionResult(granted: Boolean, showRationale: Boolean) {
        nativeGranted = granted; nativeRationale = showRationale
        permissionAsked = !granted
        persist { store.savePermissionAsked(!granted) }
        mutableState.update { it.copy(permission = permissionStatus(granted, permissionAsked, showRationale)) }
        if (granted && foreground) refresh(true)
    }
    fun selectCity(place: Place) {
        gate.next(); refreshJob?.cancel(); closeSearch()
        mutableState.update { it.copy(place = place, mode = LocationMode.MANUAL,
            weather = it.weather.takeIf { _ -> it.place?.key == place.key }, error = null,
            locationDisabled = false, notice = null, loading = false, locating = false) }
        persist { store.savePlace(place, LocationMode.MANUAL) }
        refresh(true)
    }
    fun useDeviceLocation() {
        gate.next(); refreshJob?.cancel()
        mutableState.update { it.copy(mode = LocationMode.DEVICE, loading = false, error = null, locationDisabled = false) }
        val previous = state.value.place
        persist { store.savePlace(previous, LocationMode.DEVICE) }
        refresh(true)
    }
    fun setProfile(profile: UserProfile) {
        mutableState.update { it.copy(profile = profile) }
        persist { store.saveProfile(profile) }
    }
    fun dismissNotice() { mutableState.update { it.copy(notice = null) } }

    fun refresh(force: Boolean = true) {
        val before = state.value
        if (!foreground || !before.initialized || (before.loading && !force)) return
        tick()
        if (before.mode == LocationMode.DEVICE && before.permission != PermissionStatus.GRANTED) {
            mutableState.update { it.copy(loading = false, locating = false,
                notice = if (it.weather != null) "Показываем последнюю выбранную локацию. Для нового местоположения нужен доступ или ручной выбор." else it.notice) }
            return
        }
        if (before.mode == LocationMode.MANUAL && before.place == null) return
        if (!force && before.mode == LocationMode.MANUAL && recentlyLoaded(before.weather)) return
        val id = gate.next()
        refreshJob?.cancel()
        mutableState.update { it.copy(loading = true, locating = it.mode == LocationMode.DEVICE, error = null, locationDisabled = false) }
        refreshJob = viewModelScope.launch {
            var resolvingLocation = before.mode == LocationMode.DEVICE
            try {
                val target = if (resolvingLocation) location.locate() else before.place ?: return@launch
                if (!gate.accepts(id)) return@launch
                mutableState.update { it.copy(place = target,
                    weather = it.weather.takeIf { _ -> it.place?.key == target.key }, locating = false,
                    notice = if (resolvingLocation) null else it.notice) }
                if (resolvingLocation) {
                    try { store.savePlace(target, LocationMode.DEVICE) }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) { storageNotice() }
                }
                resolvingLocation = false
                if (state.value.weather == null) {
                    val cache = try { repository.cached(target) }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) { null }
                    if (!gate.accepts(id)) return@launch
                    if (cache != null) mutableState.update { it.copy(weather = cache, now = clock.instant()) }
                }
                if (!force && recentlyLoaded(state.value.weather)) return@launch
                val result = repository.fetch(target)
                if (gate.accepts(id) && state.value.place?.key == target.key) {
                    mutableState.update { it.copy(weather = result.weather, now = clock.instant(), error = null,
                        notice = if (!result.cacheSaved) "Погода загружена, но не сохранилась для работы без интернета." else it.notice) }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (gate.accepts(id)) mutableState.update { it.copy(error = problem(e, resolvingLocation),
                    locationDisabled = e is LocationFailure && e.reason == LocationFailureReason.DISABLED,
                    now = clock.instant()) }
            } finally {
                if (gate.accepts(id)) mutableState.update { it.copy(loading = false, locating = false) }
            }
        }
    }
    private fun recentlyLoaded(weather: WeatherConditions?): Boolean = weather != null &&
        FreshnessPolicy.evaluate(weather, clock.instant()) == Freshness.CURRENT &&
        Duration.between(weather.fetchedAt, clock.instant()).seconds in 0..599

    fun search(query: String) {
        searchJob?.cancel()
        val id = searchGate.next()
        val text = query.take(100)
        mutableState.update { it.copy(searchQuery = text, searchResults = emptyList(), searchError = null, searching = text.trim().length >= 2) }
        if (text.trim().length < 2) return
        searchJob = viewModelScope.launch {
            try {
                delay(350)
                val result = repository.search(text.trim())
                if (searchGate.accepts(id)) mutableState.update { it.copy(searchResults = result) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (searchGate.accepts(id)) mutableState.update { it.copy(searchError = problem(e, false)) }
            } finally {
                if (searchGate.accepts(id)) mutableState.update { it.copy(searching = false) }
            }
        }
    }
    fun closeSearch() {
        searchGate.next(); searchJob?.cancel()
        mutableState.update { it.copy(searchQuery = "", searchResults = emptyList(), searching = false, searchError = null) }
    }
    private fun persist(block: suspend () -> Unit) {
        viewModelScope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { storageNotice() }
        }
    }
    private fun storageNotice() { mutableState.update { it.copy(notice = "Не получилось сохранить настройки. В этом сеансе они действуют, после перезапуска могут сброситься.") } }
    private fun problem(error: Exception, locationPhase: Boolean): String = when {
        error is LocationFailure && error.reason == LocationFailureReason.DISABLED -> "Геолокация выключена. Включите её в настройках телефона или выберите город вручную."
        error is LocationFailure && error.reason == LocationFailureReason.PERMISSION -> "Нет доступа к местоположению. Разрешите его в настройках приложения или выберите город вручную."
        error is LocationFailure || locationPhase -> "Не удалось определить местоположение. Попробуйте ещё раз или выберите город вручную."
        error is ApiException && error.status == 429 -> "Погодный сервис временно ограничил запросы. Повторите позже; сохранённые данные отмечены отдельно."
        error is InvalidWeatherData -> "Сервис вернул неполные или неподходящие данные. Попробуйте обновить позже."
        else -> "Не удалось загрузить данные. Проверьте интернет и повторите запрос. Ручной выбор города остаётся доступен."
    }
}
