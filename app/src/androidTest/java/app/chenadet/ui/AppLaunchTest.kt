package app.chenadet.ui

import android.graphics.Bitmap
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.chenadet.MainActivity
import app.chenadet.core.*
import app.chenadet.data.AppStore
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real Activity, system permission, HTTPS and DataStore; requires online emulator. */
@RunWith(AndroidJUnit4::class)
class AppLaunchTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val store = AppStore(instrumentation.targetContext)

    @Test fun deniedLocationStillLoadsLiveWeatherAndPersistsSettings() {
        runBlocking { store.clear() }
        ActivityScenario.launch(MainActivity::class.java).use {
            awaitText("Разрешить местоположение")
            screenshot("first-launch")
            compose.onNodeWithText("Разрешить местоположение").performClick()
            val deadline = SystemClock.elapsedRealtime() + 10_000
            var denied = false
            while (!denied && SystemClock.elapsedRealtime() < deadline) {
                val buttons = instrumentation.uiAutomation.rootInActiveWindow
                    ?.findAccessibilityNodeInfosByViewId("com.android.permissioncontroller:id/permission_deny_button")
                denied = buttons?.firstOrNull()?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
                if (!denied) SystemClock.sleep(100)
            }
            assertTrue("Android permission dialog must offer denial", denied)
            awaitText("Можно продолжить без разрешения — просто выберите город.")
            compose.onNodeWithText("Выбрать город вручную").performClick()
            compose.onNodeWithText("Название города").performTextInput("Екатеринбург")
            val cityResult = hasText("Екатеринбург") and hasClickAction() and !hasSetTextAction()
            compose.waitUntil(30_000) { compose.onAllNodes(cityResult).fetchSemanticsNodes().isNotEmpty() }
            compose.onNode(cityResult).performClick()
            compose.waitUntil(30_000) {
                compose.onAllNodesWithText("Обновляем погоду…").fetchSemanticsNodes().isEmpty() &&
                    runBlocking { store.readCache() } != null
            }
            compose.onNodeWithTag("home").performScrollToNode(hasText("Что надеть"))
            compose.onNodeWithText("Что надеть").assertIsDisplayed()
            screenshot("live-weather")
            compose.onNodeWithTag("home").performScrollToNode(hasText("Настроить"))
            compose.onNodeWithText("Настроить").performClick()
            compose.onNodeWithText(WeatherText.sensitivity(Sensitivity.COLD)).performClick()
            compose.waitUntil(5_000) { runBlocking { store.read().profile.sensitivity } == Sensitivity.COLD }
            val saved = runBlocking { store.read() }
            assertEquals(LocationMode.MANUAL, saved.mode)
            assertEquals("Екатеринбург", saved.place?.name)
        }
        ActivityScenario.launch(MainActivity::class.java).use {
            awaitText("Екатеринбург")
            compose.onNodeWithText("Настроить").performClick()
            compose.onNodeWithText(WeatherText.sensitivity(Sensitivity.COLD)).assertIsSelected()
            screenshot("saved-profile")
        }
    }

    private fun awaitText(text: String, timeout: Long = 10_000) {
        compose.waitUntil(timeout) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }
    private fun screenshot(name: String) {
        val directory = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?.let { File(it) }
            ?: File(instrumentation.targetContext.getExternalFilesDir(null), "screenshots")
        directory.mkdirs()
        File(directory, "$name.png").outputStream().use {
            checkNotNull(instrumentation.uiAutomation.takeScreenshot()).compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
