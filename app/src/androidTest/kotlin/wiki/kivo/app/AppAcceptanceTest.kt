package wiki.kivo.app

import android.content.Intent
import android.net.Uri
import android.view.WindowManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 真正运行安装后的 Compose 界面。账号页只输入测试文字，不提交生产登录。 */
@RunWith(AndroidJUnit4::class)
class AppAcceptanceTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()

    private fun ready(tag: String) {
        ui.waitUntil(30_000) { ui.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun allRootsAndModuleEntryReturnNormally() {
        ready("home_screen")
        ui.onNodeWithTag("tab_1").performClick()
        ready("catalog_screen")
        ui.onNodeWithText("角色图鉴").performClick()
        ready("student_catalog")
        ui.onNodeWithTag("tab_2").assertIsSelected()
        ui.onNodeWithTag("tab_3").performClick()
        ready("collection_screen")
        ui.onNodeWithTag("tab_4").performClick()
        ready("profile_screen")
        ui.onNodeWithTag("tab_0").performClick()
        ready("home_screen")
    }

    @Test
    fun themePersistsThroughActivityRecreation() {
        ready("home_screen")
        ui.onNodeWithTag("open_settings").performClick()
        ready("settings_screen")
        ui.onNodeWithTag("theme_DARK").performScrollTo().performClick()
        ui.waitUntil(5000) {
            ui.onAllNodes(isSelected() and hasTestTag("theme_DARK"))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        ui.activityRule.scenario.recreate()
        ready("settings_screen")
        ui.onNodeWithTag("theme_DARK").assertIsSelected()
        ui.onNodeWithTag("theme_LIGHT").performScrollTo().performClick()
        ui.waitUntil(5000) {
            ui.onAllNodes(isSelected() and hasTestTag("theme_LIGHT"))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    @Test
    fun passwordIsNotSavedAcrossRecreationAndWindowIsSecure() {
        ready("home_screen")
        ui.onNodeWithTag("tab_4").performClick()
        ready("profile_screen")
        ui.onNodeWithTag("open_login").performClick()
        ready("login_screen")
        ui.onNodeWithTag("submit_login").assertIsNotEnabled()
        ui.onNodeWithTag("account_input").performTextInput("LOCAL_UI_ONLY")
        ui.onNodeWithTag("password_input").performTextInput("LOCAL_UI_PASSWORD")
        ui.onNodeWithTag("submit_login").assertIsEnabled()
        ui.runOnIdle {
            assertTrue(
                ui.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0
            )
        }
        ui.activityRule.scenario.recreate()
        ready("login_screen")
        ui.onNodeWithTag("account_input").assertTextContains("LOCAL_UI_ONLY")
        ui.onNodeWithTag("submit_login").assertIsNotEnabled()
        ui.onNodeWithTag("back").performClick()
        ui.runOnIdle {
            assertEquals(
                0,
                ui.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE,
            )
        }
    }

    @Test
    fun bulletinReaderCanBookmarkAndReopenFromProfile() {
        ready("home_screen")
        val originalIntent = Intent(ui.activity.intent)
        ui.runOnUiThread {
            ui.activity.startActivity(
                Intent(ui.activity, MainActivity::class.java).apply {
                    data = Uri.parse("kivoarchive://content/BULLETIN/39")
                    addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
            )
        }
        ready("reader_screen")
        ui.waitUntil(30_000) {
            ui.onAllNodesWithText("26年7月站内动态").fetchSemanticsNodes().isNotEmpty()
        }
        // 公告只读请求允许访问生产内容；失败会令此测试如实失败，不以本地假内容冒充。
        if (ui.onAllNodesWithContentDescription("取消收藏").fetchSemanticsNodes().isEmpty())
            ui.onNodeWithTag("bookmark").performClick()
        ui.waitUntil(5000) {
            ui.onAllNodesWithContentDescription("取消收藏").fetchSemanticsNodes().isNotEmpty()
        }
        ui.onNodeWithTag("back").performClick()
        ready("home_screen")
        ui.onNodeWithTag("tab_4").performClick()
        ready("profile_screen")
        ui.onNodeWithText("本地收藏").performClick()
        ui.onNodeWithText("26年7月站内动态").performClick()
        ready("reader_screen")
        ui.onNodeWithContentDescription("取消收藏").assertExists()
        // ActivityScenario 按启动 Intent 匹配生命周期；测试完深链接后恢复其观察身份。
        ui.runOnUiThread { ui.activity.intent = originalIntent }
    }
}
