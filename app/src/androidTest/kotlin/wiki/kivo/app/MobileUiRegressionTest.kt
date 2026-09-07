package wiki.kivo.app

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.core.net.toUri
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import wiki.kivo.core.data.local.SettingsRepository

/** 真实公开资料验收：跟手半页、资料页尾小幅回滚和窄屏字段布局。 */
class MobileUiRegressionTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private lateinit var launchIntent: Intent

    @Before
    fun rememberIntent() {
        launchIntent = Intent(ui.activity.intent)
    }

    @After
    fun restoreIntent() {
        ui.runOnUiThread { ui.activity.intent = launchIntent }
    }

    private fun capture(name: String) {
        val target =
            File(ui.activity.getExternalFilesDir(null), "mobile-regression").apply { mkdirs() }
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(target, "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    private fun current() = ui.onNodeWithTag("character_detail")

    private fun text(value: String) =
        ui.onNode(hasText(value) and hasAnyAncestor(hasTestTag("character_detail")))

    private fun open() {
        ui.runOnUiThread {
            InstrumentationRegistry.getInstrumentation()
                .callActivityOnNewIntent(
                    ui.activity,
                    Intent(Intent.ACTION_VIEW, "kivoarchive://content/student/61".toUri()),
                )
        }
        ui.waitUntil(45000) {
            ui.onAllNodesWithTag("character_detail").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun pagerTracksHeldFingerAndInfoCanLeaveTheBottomWithSmallDrags() {
        open()
        current().performScrollToIndex(1)
        val pager = ui.onNodeWithTag("character_pager")
        pager.performTouchInput {
            down(center)
            moveBy(Offset(-30f, 0f), delayMillis = 60)
            moveBy(Offset(-width * .25f, 0f), delayMillis = 180)
        }
        try {
            val bounds = current().fetchSemanticsNode().boundsInRoot
            val container = pager.fetchSemanticsNode().boundsInRoot
            assertTrue("按住时当前页应已平移，不能只在抬手时切换", bounds.width < container.width * .95f)
            capture("pager-held")
        } finally {
            pager.performTouchInput { up() }
        }
        ui.waitForIdle()
        current().performScrollToIndex(1)
        ui.onNodeWithTag("character_tab_1").performClick()
        current().performScrollToKey("updated")
        ui.waitForIdle()
        capture("info-bottom")
        fun position() =
            current()
                .fetchSemanticsNode()
                .config[SemanticsProperties.VerticalScrollAxisRange]
                .value()
        val before = position()
        repeat(8) {
            current().performTouchInput {
                swipe(center, center + Offset(0f, height * .17f), durationMillis = 420)
            }
            ui.waitForIdle()
        }
        assertTrue("小幅下拉必须能持续返回上方资料，不能陷入加载高度反馈循环", position() < before - 1f)
        capture("info-scrolled-back")
    }

    @Test
    fun shortProfileFieldsShareRows() {
        open()
        text("身高").performScrollTo()
        val height = text("身高").fetchSemanticsNode().boundsInRoot
        val birthday = text("生日").fetchSemanticsNode().boundsInRoot
        val rarity = text("稀有度").fetchSemanticsNode().boundsInRoot
        assertEquals(height.top, birthday.top, 2f)
        assertEquals(height.top, rarity.top, 2f)
        assertTrue(birthday.left > height.left)
        capture("profile-compact")
    }

    @Test
    fun catalogFitsAtLeastThreeCharactersAcrossAtNormalFontSize() {
        val settings = SettingsRepository(ui.activity)
        val previous = runBlocking { settings.settings.first().compactCatalog }
        try {
            runBlocking { settings.setFlag("compact_catalog", false) }
            ui.waitUntil(35000) { ui.onAllNodesWithTag("tab_2").fetchSemanticsNodes().isNotEmpty() }
            ui.onNodeWithTag("tab_2").performClick()
            val cards =
                SemanticsMatcher("图鉴角色卡片") {
                    it.config
                        .getOrNull(SemanticsProperties.TestTag)
                        ?.startsWith("catalog_character_") == true
                }
            ui.waitUntil(35000) { ui.onAllNodes(cards).fetchSemanticsNodes().size >= 3 }
            val bounds =
                ui.onAllNodes(cards)
                    .fetchSemanticsNodes()
                    .map { it.boundsInRoot }
                    .filter { it.height > 0 }
            val top = bounds.minOf { it.top }
            assertTrue("正常字号手机应至少三列", bounds.count { kotlin.math.abs(it.top - top) < 2 } >= 3)
            capture("catalog-dense")
        } finally {
            runBlocking { settings.setFlag("compact_catalog", previous) }
        }
    }
}
