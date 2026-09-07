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

@RunWith(AndroidJUnit4::class)
class IterationAcceptanceTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()

    private fun ready(tag: String) {
        ui.waitUntil(30_000) { ui.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun capture(name: String) {
        ui.waitForIdle()
        val folder = File(ui.activity.getExternalFilesDir(null), "acceptance").apply { mkdirs() }
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(folder, "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    @Test
    fun globalSearchReturnsMultipleKindsAndKeepsQueryOnReturn() {
        ready("home_screen")
        ui.onNodeWithTag("open_search").performClick()
        ready("search_screen")
        capture("12-search-empty")
        ui.onNodeWithTag("global_search_input").performTextInput("星野")
        ui.onNodeWithTag("submit_search").performClick()
        try {
            ui.waitUntil(35_000) {
                ui.onAllNodesWithText("小鸟游 星野").fetchSemanticsNodes().isNotEmpty()
            }
        } catch (failure: AssertionError) {
            capture("search-failure")
            throw failure
        }
        capture("13-search-results")
        ui.onNodeWithText("小鸟游 星野").performClick()
        ready("character_detail")
        ui.onNodeWithTag("back").performClick()
        ready("search_screen")
        ui.onNodeWithTag("global_search_input").assertTextContains("星野")
    }

    @Test
    fun qqChannelOpensInsideTheAppAndReturnsToProfile() {
        ready("home_screen")
        ui.onNodeWithTag("tab_4").performClick()
        ready("profile_screen")
        ui.onNodeWithText("QQ频道").performScrollTo().performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithText("频道首页").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("浏览器打开").assertIsDisplayed()
        Thread.sleep(3000)
        capture("17-qq-channel")
        ui.onNodeWithTag("back").performClick()
        ready("profile_screen")
    }

    @Test
    fun startupAndTranslationPersistAndCommunitiesAreNative() {
        ready("home_screen")
        ui.onNodeWithTag("open_settings").performClick()
        ready("settings_screen")
        ui.onNodeWithTag("start_CHARACTERS").performScrollTo().performClick()
        ui.onNodeWithTag("translation_CN").performScrollTo().performClick()
        ui.activityRule.scenario.recreate()
        ready("settings_screen")
        ui.onNodeWithTag("start_CHARACTERS").performScrollTo().assertIsSelected()
        ui.onNodeWithTag("translation_CN").performScrollTo().assertIsSelected()
        capture("14-preferences")
        // 清理本用例的偏好，避免影响其他独立测试的初始条件。
        ui.onNodeWithTag("start_HOME").performScrollTo().performClick()
        ui.onNodeWithTag("translation_FAN").performScrollTo().performClick()
        ui.onNodeWithTag("back").performClick()
        ui.onNodeWithTag("tab_4").performClick()
        ready("profile_screen")
        ui.onNodeWithText("贡献者名单").performScrollTo().performClick()
        ready("community_contributor")
        capture("15-contributors")
        ui.onNodeWithText("NijiNeko").assertExists()
        ui.onNodeWithTag("back").performClick()
        ui.onNodeWithText("帮助我们").performScrollTo().performClick()
        ready("community_contact")
        capture("16-contact")
        ui.onNodeWithText("为古书馆添砖加瓦").assertExists()
    }
}
