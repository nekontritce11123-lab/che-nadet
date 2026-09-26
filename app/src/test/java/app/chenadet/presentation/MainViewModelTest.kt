package app.chenadet.presentation

import app.chenadet.core.*
import app.chenadet.data.*
import java.io.IOException
import java.time.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val now = Instant.parse("2026-09-26T10:15:00Z")
    private val city = Place("Тестовый город", 55.0, 60.0)
    private val weather = WeatherConditions(WeatherPoint(now, 15.0, 13.0), fetchedAt = now)
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { Dispatchers.resetMain() }
    private fun model(store: MemoryStore, repo: FakeRepository, location: LocationSource = object : LocationSource {
        override suspend fun locate() = city
    }) = MainViewModel(repo, store, location, Clock.fixed(now, ZoneOffset.UTC))

    @Test fun expiredOneTimeGrantCanBeRequestedAgain() = runTest(dispatcher) {
        val store = MemoryStore(); val vm = model(store, FakeRepository(weather))
        runCurrent(); vm.onForeground(false, false); vm.permissionRequested(); vm.permissionResult(true, false)
        advanceUntilIdle(); vm.onBackground(); vm.onForeground(false, false); runCurrent()
        assertEquals(PermissionStatus.NOT_ASKED, vm.state.value.permission)
        vm.onBackground()
    }
    @Test fun manualCityStillWorksAfterPermanentDenial() = runTest(dispatcher) {
        val store = MemoryStore(); val repo = FakeRepository(weather); val vm = model(store, repo)
        runCurrent(); vm.onForeground(false, false); vm.permissionRequested(); vm.permissionResult(false, false)
        runCurrent(); assertEquals(PermissionStatus.PERMANENTLY_DENIED, vm.state.value.permission)
        vm.selectCity(city); advanceUntilIdle()
        assertEquals(LocationMode.MANUAL, vm.state.value.mode)
        assertEquals(city, vm.state.value.place); assertEquals(weather, vm.state.value.weather)
        vm.onBackground()
    }
    @Test fun changingCityDoesNotShowOldCityWeatherWhenNetworkFails() = runTest(dispatcher) {
        val store = MemoryStore(SavedSettings(mode = LocationMode.MANUAL, place = city))
        val repo = FakeRepository(weather); val vm = model(store, repo)
        runCurrent(); vm.onForeground(false, false); advanceUntilIdle()
        assertNotNull(vm.state.value.weather)
        repo.fail = true
        val other = Place("Другой город", 10.0, 20.0)
        vm.selectCity(other); advanceUntilIdle()
        assertEquals(other, vm.state.value.place); assertNull(vm.state.value.weather)
        assertNotNull(vm.state.value.error); vm.onBackground()
    }
    @Test fun profileRecomputesWithoutAnotherNetworkRequest() = runTest(dispatcher) {
        val store = MemoryStore(SavedSettings(mode = LocationMode.MANUAL, place = city))
        val repo = FakeRepository(weather); val vm = model(store, repo)
        runCurrent(); vm.onForeground(false, false); advanceUntilIdle()
        val count = repo.requests
        vm.setProfile(UserProfile(Sensitivity.COLD, Activity.OUTDOORS)); advanceUntilIdle()
        assertEquals(count, repo.requests); assertEquals(Sensitivity.COLD, vm.state.value.profile.sensitivity)
        assertEquals(vm.state.value.profile, store.settings.profile); vm.onBackground()
    }
    @Test fun locationFailureLeavesManualSelectionAvailable() = runTest(dispatcher) {
        val store = MemoryStore(); val repo = FakeRepository(weather)
        val location = object : LocationSource { override suspend fun locate(): Place = throw IOException("location unavailable") }
        val vm = model(store, repo, location)
        runCurrent(); vm.onForeground(true, false); advanceUntilIdle()
        assertNotNull(vm.state.value.error); assertFalse(vm.state.value.loading)
        vm.selectCity(city); advanceUntilIdle(); assertEquals(city, vm.state.value.place)
        vm.onBackground()
    }
    @Test fun failedRefreshPreservesMatchingCacheButNotFakeFreshness() = runTest(dispatcher) {
        val old = weather.copy(current = weather.current.copy(at = now.minusSeconds(3 * 3600)), fetchedAt = now.minusSeconds(3 * 3600))
        val store = MemoryStore(SavedSettings(mode = LocationMode.MANUAL, place = city))
        val repo = FakeRepository(weather).apply { cache = old; fail = true }
        val vm = model(store, repo)
        runCurrent(); vm.onForeground(false, false); advanceUntilIdle()
        assertEquals(Freshness.STALE, vm.state.value.freshness); assertEquals(old, vm.state.value.weather)
        assertNotNull(vm.state.value.error); vm.onBackground()
    }
}

private class MemoryStore(var settings: SavedSettings = SavedSettings()) : SettingsStore {
    private var cache: CacheEntry? = null
    override suspend fun read() = settings
    override suspend fun saveProfile(profile: UserProfile) { settings = settings.copy(profile = profile) }
    override suspend fun savePlace(place: Place?, mode: LocationMode) { settings = settings.copy(place = place, mode = mode) }
    override suspend fun savePermissionAsked(asked: Boolean) { settings = settings.copy(permissionAsked = asked) }
    override suspend fun readCache() = cache
    override suspend fun saveCache(entry: CacheEntry) { cache = entry }
    override suspend fun clear() { settings = SavedSettings(); cache = null }
}
private class FakeRepository(private val response: WeatherConditions) : WeatherRepository {
    var requests = 0; var fail = false; var cache: WeatherConditions? = null
    override suspend fun fetch(place: Place): WeatherLoad { requests++; if (fail) throw IOException("offline"); return WeatherLoad(response) }
    override suspend fun cached(place: Place) = cache
    override suspend fun search(query: String) = emptyList<Place>()
}
