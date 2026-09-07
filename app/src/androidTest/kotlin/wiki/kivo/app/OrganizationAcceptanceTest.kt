package wiki.kivo.app

import android.content.Intent
import android.graphics.Bitmap
import android.net.ConnectivityManager
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.core.net.toUri
import androidx.test.espresso.Espresso
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

/** 仅访问公开 GET；测试使用独立 Debug 包，不修改正式预览资料或生产站点。 */
class OrganizationAcceptanceTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private lateinit var originalIntent: Intent

    @Before
    fun prepare() {
        originalIntent = Intent(ui.activity.intent)
        ready("tab_0")
        if (InstrumentationRegistry.getArguments().getString("expectOffline") == "true")
            assertNull(
                "离线验收必须确认模拟器确实断网",
                ui.activity.getSystemService(ConnectivityManager::class.java).activeNetwork,
            )
    }

    @After
    fun restore() {
        ui.runOnUiThread { ui.activity.intent = originalIntent }
    }

    private fun ready(tag: String) =
        ui.waitUntil(40000) { ui.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }

    private fun open(type: String, id: Int) {
        ui.runOnUiThread {
            InstrumentationRegistry.getInstrumentation()
                .callActivityOnNewIntent(
                    ui.activity,
                    Intent(Intent.ACTION_VIEW, "kivoarchive://content/$type/$id".toUri()),
                )
        }
        ready(if (type == "student") "character_detail" else "organization_detail")
        ui.onNodeWithTag(if (type == "student") "character_detail" else "organization_detail")
            .performScrollToIndex(1)
    }

    private fun capture(name: String) {
        ui.waitForIdle()
        val dir =
            File(ui.activity.getExternalFilesDir(null), "organization-acceptance").apply {
                mkdirs()
            }
        val prefix = InstrumentationRegistry.getArguments().getString("capturePrefix") ?: "phone"
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(dir, "$prefix-$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    @Test
    fun categoryTapPreservesHeaderAndVerticalIntentDoesNotSwitch() {
        open("student", 86)
        ready("character_tab_0")
        ui.onNodeWithTag("character_detail").performScrollToIndex(1)
        val before = ui.onNodeWithTag("character_tab_0").fetchSemanticsNode().boundsInRoot.top
        ui.onNodeWithTag("character_tab_1").performClick()
        ui.onNodeWithTag("character_tab_1").assertIsSelected()
        assertEquals(
            before,
            ui.onNodeWithTag("character_tab_1").fetchSemanticsNode().boundsInRoot.top,
            2f,
        )
        ui.onNodeWithTag("character_detail").performTouchInput {
            swipe(Offset(width * .55f, height * .75f), Offset(width * .45f, height * .30f), 650)
        }
        ui.onNodeWithTag("character_tab_1").assertIsSelected()
        capture("character-stable-tabs")
    }

    @Test
    fun horizontalGestureSwitchesOnceAndLinksReachOrganizationAndRelation() {
        open("student", 86)
        ready("character_tab_0")
        ui.onNodeWithTag("character_detail").performScrollToIndex(1)
        // 分类栏下面的留白发起明确横滑，避免把子控件的横滚误当作页面切换。
        ui.onNodeWithTag("character_detail").performTouchInput {
            swipe(Offset(width * .8f, 130f), Offset(width * .2f, 134f), 450)
        }
        ui.onNodeWithTag("character_tab_1").assertIsSelected()
        // 使用实际目录入口定位末尾，避免测试框架逐屏扫过数百个异步图文块后跳过标题。
        ui.onNodeWithText("本页目录").performClick()
        ui.onNodeWithText("搜索选项").performTextInput("相关")
        Espresso.closeSoftKeyboard()
        ui.onNodeWithText("与之相关的角色").performClick()

        ready("relation_19")
        // 实际向下拖动露出关系标题，避免在吸顶栏覆盖的区域注入点击。
        ui.onNodeWithTag("character_detail").performTouchInput {
            swipe(Offset(width * .5f, height * .35f), Offset(width * .5f, height * .65f), 650)
        }
        capture("related-characters")
        // 这里验证可访问点击的路由；相同关系组件的实际触摸在地图／成员用例中覆盖。
        ui.onNodeWithTag("relation_19").performSemanticsAction(SemanticsActions.OnClick) { it() }
        capture("after-related-click")
        ready("organization_section_1")
        ui.onNodeWithTag("organization_section_1").performClick()
        ui.onNodeWithTag("organization_detail").performScrollToNode(hasText("资料目录"))
        capture("relation-description")
        Espresso.pressBack()
        ready("character_detail")
        ui.onNodeWithTag("character_detail").performScrollToIndex(0)
        ui.onNodeWithTag("character_detail").performScrollToNode(hasText("阿比多斯高中"))
        ui.onNodeWithText("阿比多斯高中").performClick()
        ready("organization_section_0")
        capture("organization-overview")
    }

    @Test
    fun abydosMapLandmarksRelationsAndMembersAreNavigable() {
        open("school", 1)
        ready("organization_section_1")
        ui.onNodeWithTag("organization_detail").performScrollToIndex(1)
        ui.onNodeWithTag("organization_section_1").performClick()
        ready("map_mark_0")
        capture("map")
        ui.onNodeWithTag("map_mark_0").performClick()
        ui.onAllNodesWithText("阿比多斯主楼").onLast().assertIsDisplayed()
        ui.onNodeWithText("阿比多斯的学员们主要活动", substring = true).assertExists()
        capture("landmark")
        ui.onNodeWithContentDescription("关闭地标资料").performClick()
        ui.onNodeWithTag("organization_section_3").performClick()
        ui.onNodeWithTag("organization_detail").performScrollToNode(hasTestTag("relation_19"))
        ready("relation_19")
        ui.onNodeWithTag("relation_19").performClick()
        ready("organization_section_0")
        ui.onNodeWithTag("organization_detail").performScrollToNode(hasTestTag("member_76"))
        ui.onNodeWithTag("member_76").performClick()
        ready("character_detail")
        capture("member-character")
    }

    @Test
    fun settingsDisableSwipePersistAfterRecreationAndKeepTapAvailable() {
        val preferences = SettingsRepository(ui.activity)
        val original = runBlocking { preferences.settings.first().swipeCategories }
        try {
            runBlocking { preferences.setFlag("swipe_categories", true) }
            ui.onNodeWithTag("open_settings").performClick()
            ui.onNodeWithTag("settings_screen").performScrollToNode(hasText("左右滑动切换"))
            ui.onNodeWithText("左右滑动切换").performClick()
            ui.waitUntil(5000) { !runBlocking { preferences.settings.first().swipeCategories } }
            capture("swipe-setting")
            ui.activityRule.scenario.recreate()
            ready("settings_screen")
            assertFalse(runBlocking { preferences.settings.first().swipeCategories })
            open("student", 86)
            ui.onNodeWithTag("character_detail").performTouchInput {
                swipe(Offset(width * .8f, 130f), Offset(width * .2f, 134f), 450)
            }
            ui.onNodeWithTag("character_tab_0").assertIsSelected()
            ui.onNodeWithTag("character_tab_1").performClick()
            ui.onNodeWithTag("character_tab_1").assertIsSelected()
        } finally {
            runBlocking { preferences.setFlag("swipe_categories", original) }
        }
    }

    @Test
    fun partialHeaderAndShortNpcTabsStayAtTheirScreenPosition() {
        for (id in listOf(86, 619)) {
            open("student", id)
            ready("character_tab_0")
            ui.onNodeWithTag("character_detail").performSemanticsAction(SemanticsActions.ScrollBy) {
                it(0f, -650f)
            }
            val top = ui.onNodeWithTag("character_tab_0").fetchSemanticsNode().boundsInRoot.top
            for (tab in listOf(1, 2, 3, 0)) {
                ui.onNodeWithTag("character_tab_$tab").performClick()
                ui.onNodeWithTag("character_tab_$tab").assertIsSelected()
                assertEquals(
                    "角色 $id 分类 $tab 公共头部位移",
                    top,
                    ui.onNodeWithTag("character_tab_$tab").fetchSemanticsNode().boundsInRoot.top,
                    2f,
                )
            }
        }
    }

    @Test
    fun catalogSearchAndReturnRetainQuery() {
        val preferences = SettingsRepository(ui.activity)
        val original = runBlocking { preferences.settings.first().compactOrganization }
        try {
            runBlocking { preferences.setFlag("compact_organization", false) }
            ui.onNodeWithTag("tab_1").performClick()
            ui.onNodeWithText("组织笔记").performClick()
            ready("organization_catalog")
            ui.waitUntil(40000) {
                ui.onAllNodesWithTag("organization_1").fetchSemanticsNodes().isNotEmpty()
            }
            capture("catalog")
            ui.onNodeWithContentDescription("切换紧凑列表视图").performClick()
            ui.waitUntil(5000) { runBlocking { preferences.settings.first().compactOrganization } }
            capture("catalog-list")
            ui.activityRule.scenario.recreate()
            ready("organization_catalog")
            ui.onNodeWithContentDescription("切换卡片视图").assertExists()
            ui.onNodeWithText("搜索组织 · 支持国服与民间译名").performTextInput("阿拜多斯")
            ui.onNodeWithTag("organization_1").assertExists()
            ui.onNodeWithTag("organization_1").performClick()
            // 200% 字体下分类栏可能在首屏之外，先确认页面加载，再滚入可见区域。
            ready("organization_detail")
            ui.onNodeWithTag("organization_detail").performScrollToIndex(1)
            ready("organization_section_0")
            Espresso.pressBack()
            ready("organization_catalog")
            ui.onNodeWithText("阿拜多斯").assertExists()
            capture("catalog-search")
            ui.onNodeWithContentDescription("切换卡片视图").assertExists()
        } finally {
            runBlocking { preferences.setFlag("compact_organization", original) }
        }
    }

    @Test
    fun actualCharacterMarkdownDiagramAndOrganizationBodyRender() {
        open("student", 86)
        ui.onNodeWithTag("character_tab_1").performClick()
        ui.onNodeWithText("本页目录").performClick()
        ui.onNodeWithText("搜索选项").performTextInput("人物关系链")
        ui.onNodeWithText("七、人物关系链").performScrollTo().performClick()
        ui.onNodeWithTag("character_detail").performScrollToNode(hasTestTag("mermaid_block"))
        ui.waitUntil(25000) { ui.onAllNodesWithText("图表已绘制").fetchSemanticsNodes().isNotEmpty() }
        capture("character-mermaid")
        open("school", 1)
        ui.onNodeWithTag("organization_section_2").performClick()
        ui.onNodeWithTag("organization_detail").performScrollToNode(hasText("简介"))
        capture("organization-body")
        ui.onNodeWithTag("organization_detail").performScrollToNode(hasText("资料目录"))
        ui.onNodeWithText("资料目录").performClick()
        ready("organization_toc")
        // 目录允许编辑者修改标题；验证实际生成的目录项能导航，避免依赖缓存修订中的文案。
        ui.onAllNodes(
                SemanticsMatcher("组织正文标题") {
                    it.config
                        .getOrElse(androidx.compose.ui.semantics.SemanticsProperties.TestTag) { "" }
                        .startsWith("organization_heading_")
                }
            )
            .onFirst()
            .performClick()
        capture("organization-body-toc")
    }
}
