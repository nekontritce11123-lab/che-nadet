package app.chenadet.ui

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.chenadet.core.*
import app.chenadet.presentation.HomeState
import java.io.File
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Deterministic UI fixtures. These do NOT verify the live weather service or system permission dialog. */
@RunWith(AndroidJUnit4::class)
class HomeScreenTest {
    @get:Rule val compose = createComposeRule()
    private val now = Instant.parse("2026-09-26T10:00:00Z")
    private val weather = WeatherConditions(WeatherPoint(now, 10.0, 7.0,
        humidityPct = 70, windMs = 6.0, gustMs = 9.0, precipitationMmH = 0.0,
        rainMmH = 0.0, snowCmH = 0.0, code = 3, uv = 1.0, isDay = true, cloudPct = 85))
    private fun ready() = HomeState(initialized = true, mode = LocationMode.MANUAL,
        place = Place("Тестовый город", 55.0, 60.0), weather = weather, now = now)

    @Test fun denialStillAllowsManualSearch() {
        var query = ""
        compose.setContent {
            CheNadetTheme { HomeScreen(HomeState(initialized = true,
                permission = PermissionStatus.PERMANENTLY_DENIED), HomeActions(search = { query = it })) }
        }
        compose.onNodeWithTag("home").performScrollToNode(hasText("Выбрать город вручную"))
        compose.onNodeWithText("Выбрать город вручную").performClick()
        compose.onNodeWithText("Название города").performTextInput("Тест")
        compose.runOnIdle { assertEquals("Тест", query) }
    }
    @Test fun weatherMetricOpensExplanation() {
        compose.setContent { CheNadetTheme { HomeScreen(ready(), HomeActions()) } }
        compose.onNodeWithTag("home").performScrollToNode(hasText("Ветер"))
        compose.onNodeWithText("Ветер").performClick()
        compose.onNodeWithText("Понятно").assertIsDisplayed()
    }
    @Test fun sensitivityChoiceInvokesPersonalization() {
        var chosen = UserProfile()
        compose.setContent { CheNadetTheme { HomeScreen(ready(), HomeActions(setProfile = { chosen = it })) } }
        compose.onNodeWithText("Настроить").performClick()
        compose.onNodeWithText(WeatherText.sensitivity(Sensitivity.COLD)).performClick()
        compose.runOnIdle { assertEquals(Sensitivity.COLD, chosen.sensitivity) }
    }
    @Test fun expiredCacheDoesNotShowOutfitAsCurrent() {
        val old = ready().copy(now = now.plusSeconds(7 * 3600))
        compose.setContent { CheNadetTheme { HomeScreen(old, HomeActions()) } }
        compose.onNodeWithTag("home").performScrollToNode(hasText("Получить актуальную погоду"))
        compose.onNodeWithText("Получить актуальную погоду").assertIsDisplayed()
        compose.onNodeWithText("Что надеть").assertDoesNotExist()
    }
    @Test fun darkThemeAndLargeTextKeepOutfitReachable() {
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 2f)) {
                CheNadetTheme(darkTheme = true) { HomeScreen(ready(), HomeActions()) }
            }
        }
        compose.onNodeWithTag("home").performScrollToNode(hasText("Что надеть"))
        compose.onNodeWithText("Что надеть").assertIsDisplayed()
        // Only an actual device/emulator run produces this file. Never supplied as a pre-rendered mock.
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        File(directory, "dark-large-text.png").outputStream().use {
            compose.onNodeWithTag("home").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
