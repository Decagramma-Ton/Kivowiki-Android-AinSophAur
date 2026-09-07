package wiki.kivo.app

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 同一 APK 在专用模拟器不同窗口/字号配置下运行；由验收命令设置并恢复设备配置。 */
@RunWith(AndroidJUnit4::class)
class AdaptiveAcceptanceTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()

    private fun ready(tag: String) {
        ui.waitUntil(30_000) { ui.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun capture(name: String) {
        ui.waitForIdle()
        Thread.sleep(1500)
        val prefix = InstrumentationRegistry.getArguments().getString("capturePrefix", "adaptive")
        val folder = File(ui.activity.getExternalFilesDir(null), "acceptance").apply { mkdirs() }
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(folder, "$prefix-$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    @Test
    fun navigationAndSettingsRemainUsableAtCurrentWindowSize() {
        ready("home_screen")
        ui.onNodeWithTag("open_settings").assertIsDisplayed()
        for (i in 0..4) ui.onNodeWithTag("tab_$i").assertIsDisplayed()
        ui.onNodeWithTag("home_feed").performScrollToIndex(0)
        if (ui.activity.resources.configuration.screenWidthDp >= 1200)
            ui.onNodeWithText("随身的古书馆").assertIsDisplayed()
        capture("home")
        ui.onNodeWithTag("open_settings").performClick()
        ready("settings_screen")
        ui.onNodeWithTag("settings_screen").performScrollToNode(hasTestTag("theme_DARK"))
        ui.onNodeWithTag("theme_DARK").performClick()
        ui.onNodeWithTag("settings_screen").performScrollToNode(hasText("150%"))
        capture("settings")
        ui.onNodeWithText("150%").assertIsDisplayed()
        ui.onNodeWithTag("settings_screen").performScrollToNode(hasTestTag("theme_LIGHT"))
        ui.onNodeWithTag("theme_LIGHT").performClick()
        ui.onNodeWithTag("back").performClick()
        ready("home_screen")
        ui.onNodeWithTag("tab_4").performClick()
        ready("profile_screen")
        ui.onNodeWithTag("open_login").performScrollTo().assertIsDisplayed()
        capture("profile")
    }
}
