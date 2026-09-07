package wiki.kivo.app

import android.net.ConnectivityManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 由专用离线验收命令执行；正常联网套件也可检查同样的缓存阅读路径。 */
@RunWith(AndroidJUnit4::class)
class OfflineAcceptanceTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()

    @Test
    fun previouslyOpenedBulletinRemainsReadable() {
        if (InstrumentationRegistry.getArguments().getString("expectOffline") == "true")
            assertNull(ui.activity.getSystemService(ConnectivityManager::class.java).activeNetwork)
        ui.waitUntil(30_000) {
            ui.onAllNodesWithTag("home_screen").fetchSemanticsNodes().isNotEmpty()
        }
        ui.onNodeWithContentDescription("馆内公告").performClick()
        ui.waitUntil(30_000) {
            ui.onAllNodesWithText("26年7月站内动态").fetchSemanticsNodes().isNotEmpty()
        }
        ui.onNodeWithText("26年7月站内动态").performClick()
        ui.waitUntil(30_000) {
            ui.onAllNodesWithTag("reader_screen").fetchSemanticsNodes().isNotEmpty()
        }
        ui.waitUntil(30_000) {
            ui.onAllNodes(hasTestTag("bookmark") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        ui.onNodeWithTag("reader_body").performScrollToIndex(0)
        ui.onNodeWithText("26年7月站内动态").assertIsDisplayed()
        ui.onNodeWithTag("bookmark").assertIsEnabled()
        ui.onNodeWithContentDescription("刷新资料").performClick()
        ui.onNodeWithText("26年7月站内动态").assertIsDisplayed()
        ui.onNodeWithTag("reader_body").performScrollToIndex(4)
        ui.onNodeWithText("近期更新").assertExists()
    }
}
