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

/** 从实际安装界面导出验收截图；不包含账号输入页，不向应用注入演示数据。 */
@RunWith(AndroidJUnit4::class)
class VisualAcceptanceTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()

    private fun ready(tag: String) {
        ui.waitUntil(30_000) { ui.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun capture(name: String) {
        ui.waitForIdle()
        Thread.sleep(1000) // 图片异步解码完成后留一帧，避免只截到网络占位图。
        val folder = File(ui.activity.getExternalFilesDir(null), "acceptance").apply { mkdirs() }
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(folder, "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    @Test
    fun capturePhoneTourAndCheckTabScrollRestoration() {
        ready("home_screen")
        ui.waitUntil(30_000) { ui.onAllNodesWithText("近期编辑").fetchSemanticsNodes().isNotEmpty() }
        val newsTop = ui.onNodeWithTag("home_news").fetchSemanticsNode().boundsInRoot.top
        val recentTop = ui.onNodeWithTag("home_recent").fetchSemanticsNode().boundsInRoot.top
        check(newsTop < recentTop) { "古书馆资讯应位于近期编辑之前" }
        ui.onNodeWithTag("open_settings").performClick()
        ready("settings_screen")
        ui.onNodeWithTag("theme_LIGHT").performScrollTo().performClick()
        ui.onNodeWithTag("back").performClick()
        ui.onNodeWithTag("home_feed").performScrollToIndex(0)
        capture("01-home-light")
        ui.onNodeWithTag("home_feed").performScrollToKey("schedule")
        capture("02-schedules")
        ui.onNodeWithTag("server_cn").performClick()
        Thread.sleep(1800)
        capture("03-schedules-cn")
        ui.onNodeWithTag("server_global").performClick()
        ui.onNodeWithText("国际服日程接口暂未开放，后续接入后会在这里展示。").assertExists()
        capture("04-schedules-global")
        ui.onNodeWithTag("server_jp").performClick()
        ui.onNodeWithTag("home_feed").performScrollToKey("recent")
        capture("05-home-archive")
        ui.onNodeWithTag("tab_4").performClick()
        ready("profile_screen")
        capture("06-profile-light")
        ui.onNodeWithTag("tab_0").performClick()
        ready("home_screen")
        ui.onNodeWithText("近期编辑").assertIsDisplayed() // 切换根标签不应丢失滚动位置。
        ui.onNodeWithTag("home_feed").performScrollToKey("birthdays")
        ui.onNodeWithText("本周生日").assertIsDisplayed()
        capture("07-birthdays-history")
        ui.onNodeWithTag("tab_1").performClick()
        capture("08-catalog")
        ui.onNodeWithTag("tab_2").performClick()
        ready("student_catalog")
        capture("09-students")
        ui.onNodeWithTag("tab_3").performClick()
        capture("09-collection")
        ui.onNodeWithTag("open_settings").performClick()
        ready("settings_screen")
        ui.onNodeWithTag("theme_DARK").performScrollTo().performClick()
        capture("10-settings-dark")
        ui.onNodeWithTag("back").performClick()
        ui.onNodeWithTag("tab_0").performClick()
        ui.onNodeWithTag("home_feed").performScrollToIndex(0)
        capture("11-home-dark")
        ui.onNodeWithTag("open_settings").performClick()
        ui.onNodeWithTag("theme_SYSTEM").performScrollTo().performClick()
    }
}
