package wiki.kivo.app

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import wiki.kivo.core.data.local.SettingsRepository

class SettingsBudgetUiTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()

    @Test
    fun budgetSelectionPersistsAndSpineControlsAreReadable() {
        val preferences = SettingsRepository(ui.activity)
        val original = runBlocking { preferences.settings.first() }
        val originalIntent = android.content.Intent(ui.activity.intent)
        try {
            ui.waitUntil(30_000) {
                ui.onAllNodesWithTag("open_settings").fetchSemanticsNodes().isNotEmpty()
            }
            ui.onNodeWithTag("open_settings").performClick()
            ui.onNodeWithTag("settings_screen").performScrollToNode(hasText("启用 GPU 渲染"))
            ui.onNodeWithText("启用 GPU 渲染").performScrollTo().assertIsDisplayed()
            ui.onNodeWithText("自动修复光照").performScrollTo().assertIsDisplayed()
            ui.onNodeWithText("启用实验性渲染").performScrollTo().assertIsDisplayed()
            capture("spine-settings")
            ui.onNodeWithTag("settings_screen").performScrollToNode(hasTestTag("cache_MB_512"))
            ui.onNodeWithTag("cache_MB_512").performClick()
            ui.onNodeWithTag("cache_MB_512").assertIsSelected()
            ui.activityRule.scenario.recreate()
            ui.waitUntil(10_000) {
                ui.onAllNodesWithTag("settings_screen").fetchSemanticsNodes().isNotEmpty()
            }
            ui.onNodeWithTag("settings_screen").performScrollToNode(hasTestTag("cache_MB_512"))
            ui.onNodeWithTag("cache_MB_512").assertIsSelected()
            ui.onNodeWithTag("cache_UNLIMITED").performScrollTo().performClick()
            ui.onNodeWithTag("cache_UNLIMITED").assertIsSelected()
            capture("cache-settings")
            ui.runOnUiThread {
                InstrumentationRegistry.getInstrumentation()
                    .callActivityOnNewIntent(
                        ui.activity,
                        android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse("kivoarchive://content/student/346"),
                        ),
                    )
            }
            ui.waitUntil(35_000) {
                ui.onAllNodesWithTag("character_detail").fetchSemanticsNodes().isNotEmpty()
            }
            val skill =
                SemanticsMatcher("技能卡片") {
                    it.config
                        .getOrElse(androidx.compose.ui.semantics.SemanticsProperties.TestTag) { "" }
                        .startsWith("skill_")
                }
            ui.onNodeWithTag("character_detail").performScrollToNode(skill)
            capture("skill-icons")
        } finally {
            // 与角色验收一致：深链会更新 Activity.intent，退出前恢复 Scenario 持有的身份。
            ui.runOnUiThread { ui.activity.intent = originalIntent }
            runBlocking { preferences.setCacheLimit(original.cacheLimit) }
        }
    }

    private fun capture(name: String) {
        ui.waitForIdle()
        val output =
            File(ui.activity.getExternalFilesDir(null), "settings-spine-regression").apply {
                mkdirs()
            }
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        try {
            File(output, "$name.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        } finally {
            bitmap.recycle()
        }
    }
}
