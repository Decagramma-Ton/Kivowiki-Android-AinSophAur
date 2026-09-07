package wiki.kivo.app

import android.content.Intent
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import wiki.kivo.core.media.ModelPreviewView
import wiki.kivo.core.media.SpinePreviewView

/** 源站只读验收，不点击生产环境表态按钮。测试截图由应用自身目录导出。 */
@RunWith(AndroidJUnit4::class)
class CharacterAcceptanceTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private lateinit var originalIntent: Intent

    @Before
    fun rememberLaunch() {
        originalIntent = Intent(ui.activity.intent)
    }

    @After
    fun restoreLaunch() {
        ui.runOnUiThread { ui.activity.intent = originalIntent }
    }

    private val prefix
        get() = InstrumentationRegistry.getArguments().getString("capturePrefix") ?: "phone"

    private fun ready(tag: String) {
        ui.waitUntil(35_000) { ui.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun capture(name: String) {
        ui.waitForIdle()
        Thread.sleep(500)
        val dir =
            File(ui.activity.getExternalFilesDir(null), "character-acceptance").apply { mkdirs() }
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(dir, "$prefix-$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    private fun open(id: Int) {
        // 向测试所拥有的 Activity 交付 Android 深链回调，避免新 task 覆盖 ActivityScenario。
        ui.runOnUiThread {
            InstrumentationRegistry.getInstrumentation()
                .callActivityOnNewIntent(
                    ui.activity,
                    Intent(Intent.ACTION_VIEW, "kivoarchive://content/student/$id".toUri()),
                )
        }
        ready("character_detail")
        ui.waitUntil(35_000) {
            ui.onAllNodesWithText("CHARACTER · ${id.toString().padStart(3,'0')}")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        ui.onNodeWithTag("character_detail").performScrollToIndex(0)
    }

    private fun selectTab(index: Int) {
        ui.onNodeWithTag("character_detail").performScrollToIndex(1)
        ui.onNodeWithTag("character_tab_$index").performClick()
        ui.waitUntil(8000) {
            ui.onAllNodes(hasTestTag("character_tab_$index") and isSelected())
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        ui.waitForIdle()
    }

    @Test
    fun catalogSearchFiltersAndViewChoicePersistOnReturn() {
        ready("home_screen")
        ui.onNodeWithTag("tab_2").performClick()
        ready("student_catalog")
        ui.onNodeWithTag("catalog_search").performTextInput("白子")
        ui.waitUntil(35_000) {
            ui.onAllNodesWithText("白子", substring = true).fetchSemanticsNodes().size > 2
        }
        ui.onNodeWithTag("catalog_search").performImeAction()
        Espresso.closeSoftKeyboard()
        if (ui.onAllNodesWithContentDescription("切换卡片视图").fetchSemanticsNodes().isNotEmpty())
            ui.onNodeWithTag("catalog_view").performClick()
        capture("catalog")
        ui.onNodeWithTag("catalog_view").performClick()
        capture("catalog-list")
        ui.onNodeWithTag("catalog_filters").performClick()
        capture("filters")
        ui.onNodeWithText("取消", useUnmergedTree = true).performClick()
        ui.onAllNodesWithText("白子", substring = false).onLast().performClick()
        ready("character_detail")
        ui.onNodeWithTag("back").performClick()
        ready("student_catalog")
        ui.onNodeWithTag("catalog_search").assertTextContains("白子")
    }

    @Test
    fun sixCharacterTypesExposeNativeDataAndAllTabs() {
        ready("home_screen")
        for (id in listOf(86, 373, 346, 136, 619, 593)) {
            open(id)
            capture("character-$id")
            if (id == 373) {
                ui.onNodeWithTag("character_detail").performScrollToIndex(1)
                ui.onNodeWithTag("character_detail").performScrollToIndex(0)
                ui.onNodeWithTag("combat_mode_1").performScrollTo().performClick()
                capture("hoshino-attack")
            }
            selectTab(1)
            ui.onNodeWithTag("character_detail").performScrollToIndex(1)
            capture("info-$id")
            selectTab(3)
            ui.onNodeWithTag("character_detail").performScrollToIndex(1)
            capture("voice-$id")
            if (id == 86) {
                ui.onNodeWithText("国语 74").performClick()
                ui.onNodeWithText("国语 74").assertExists()
                capture("voice-cn")
            }
        }
    }

    @Test
    fun allFourNativePreviewTypesLoadAndClose() {
        ready("home_screen")
        open(86)
        selectTab(2)
        for ((id, spine) in listOf(1562 to true, 467 to true, 440 to false, 87 to false)) {
            ui.onNodeWithTag("character_detail")
                .performScrollToKey("${if(spine)"spine" else "model"}-$id")
            ready("media_$id")
            ui.waitUntil(35_000) {
                ui.onAllNodesWithTag("media_$id").fetchSemanticsNodes().any {
                    it.config.contains(SemanticsActions.OnClick)
                }
            }
            ui.onNodeWithTag("media_$id").performSemanticsAction(SemanticsActions.OnClick) { it() }
            try {
                ui.waitUntil(90_000) {
                    ui.onAllNodesWithText("保存画面").fetchSemanticsNodes().any {
                        it.config
                            .contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled)
                            .not()
                    }
                }
                // 纹理上传和首帧完成后再取样；不把“控件出现”误判为 GPU 已显示。
                Thread.sleep(3500)
                capture("preview-$id")
                ui.onAllNodesWithText("重新下载并重试").assertCountEquals(0)
                ui.onNodeWithText("暂停").performClick()
                ui.onNodeWithText("播放").assertExists()
                capture("preview-$id-paused")
                ui.onNodeWithText("镜头复位").performClick()
                if (Build.VERSION.SDK_INT >= 29) {
                    fun descendants(view: View): Sequence<View> = sequence {
                        yield(view)
                        if (view is ViewGroup)
                            for (i in 0 until view.childCount) yieldAll(
                                descendants(view.getChildAt(i))
                            )
                    }
                    var native: View? = null
                    ui.runOnIdle {
                        native =
                            WindowInspector.getGlobalWindowViews()
                                .asSequence()
                                .flatMap { descendants(it) }
                                .first {
                                    if (spine) it is SpinePreviewView else it is ModelPreviewView
                                }
                    }
                    ui.runOnIdle {
                        val bitmap =
                            when (val view = native) {
                                is SpinePreviewView -> view.snapshot(360)
                                is ModelPreviewView -> view.snapshot(360)
                                else -> error("没有原生预览")
                            }
                        val colors = mutableSetOf<Int>()
                        for (y in 0 until bitmap.height step 6) for (x in
                            0 until bitmap.width step 6) colors += bitmap.getPixel(x, y)
                        assertTrue("必须有实际图像，不能仅渲染纯色背景", colors.size > 12)
                        val output =
                            File(
                                ui.activity.getExternalFilesDir(null),
                                "character-acceptance/$prefix-native-$id.png",
                            )
                        output.outputStream().use {
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                        }
                        bitmap.recycle()
                    }
                    // 检查独立导出画布的实际像素，防止把放大 TextureView 截图误认为完整导出。
                    runBlocking {
                        withContext(Dispatchers.Main) {
                            val full =
                                when (val view = native) {
                                    is SpinePreviewView -> {
                                        view.playing = false
                                        val first = view.fullFrameSource(768)()
                                        view.zoomBy(2f)
                                        val second = view.fullFrameSource(768)()
                                        assertTrue("完整导出不得随用户缩放裁剪", first.sameAs(second))
                                        second.recycle()
                                        first
                                    }
                                    is ModelPreviewView -> view.fullSnapshot(768)
                                    else -> error("没有原生预览")
                                }
                            try {
                                val colors = mutableSetOf<Int>()
                                for (y in 0 until full.height step 8) for (x in
                                    0 until full.width step 8) colors += full.getPixel(x, y)
                                assertTrue("离屏导出必须包含真实角色像素", colors.size > 12)
                                File(
                                        ui.activity.getExternalFilesDir(null),
                                        "character-acceptance/$prefix-full-$id.png",
                                    )
                                    .outputStream()
                                    .use { full.compress(Bitmap.CompressFormat.PNG, 100, it) }
                            } finally {
                                full.recycle()
                                (native as? ModelPreviewView)?.finishExport()
                            }
                        }
                    }
                    // 真正串起离屏渲染与 AVC 编码，覆盖连续回读、动画采样和最终 MP4。
                    if (id == 1562 || id == 440)
                        runBlocking {
                            withContext(Dispatchers.Main) {
                                val view = native
                                val source = (view as? SpinePreviewView)?.fullFrameSource(480, 1)
                                var frame = 0
                                (view as? ModelPreviewView)?.active = false
                                val output =
                                    File(
                                        ui.activity.getExternalFilesDir(null),
                                        "character-acceptance/$prefix-full-$id.mp4",
                                    )
                                try {
                                    wiki.kivo.core.media.CharacterExport.video(
                                        output,
                                        1,
                                        snapshot = {
                                            source?.invoke()
                                                ?: (view as ModelPreviewView).fullSnapshot(
                                                    480,
                                                    frame++ / 24f,
                                                )
                                        },
                                        progress = {},
                                    )
                                    assertTrue("完整画布录像必须输出非空 MP4", output.length() > 1000)
                                    android.media.MediaMetadataRetriever().use { reader ->
                                        reader.setDataSource(output.path)
                                        assertTrue(
                                            reader
                                                .extractMetadata(
                                                    android.media.MediaMetadataRetriever
                                                        .METADATA_KEY_DURATION
                                                )!!
                                                .toLong() >= 900
                                        )
                                        val frameImage = requireNotNull(reader.getFrameAtTime(0))
                                        assertTrue(frameImage.width > 0 && frameImage.height > 0)
                                        frameImage.recycle()
                                    }
                                } finally {
                                    (view as? ModelPreviewView)?.finishExport()
                                    (view as? ModelPreviewView)?.active = true
                                }
                            }
                        }
                    ui.runOnUiThread { ui.activity.intent = originalIntent }
                    ui.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
                    try {
                        assertFalse(
                            when (val view = native) {
                                is SpinePreviewView -> view.active
                                is ModelPreviewView -> view.active
                                else -> true
                            }
                        )
                    } finally {
                        ui.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
                    }
                    ui.waitForIdle()
                }
            } catch (e: Throwable) {
                capture("preview-$id-failure")
                throw e
            } finally {
                if (ui.onAllNodesWithContentDescription("关闭预览").fetchSemanticsNodes().isNotEmpty())
                    ui.onNodeWithContentDescription("关闭预览").performClick()
            }
            ready("character_detail")
        }
    }

    @Test
    fun cachedCharacterAndMediaRemainReadableOffline() {
        org.junit.Assume.assumeTrue(
            InstrumentationRegistry.getArguments().getString("expectOffline") == "true"
        )
        // 不能仅靠“使用了缓存”判定离线成功，设备必须确实没有可用网络。
        assertNull(ui.activity.getSystemService(ConnectivityManager::class.java).activeNetwork)
        ready("home_screen")
        open(86)
        selectTab(1)
        ui.onNodeWithTag("character_detail").performScrollToIndex(1)
        ui.onAllNodesWithText("女主人公级别", substring = true).assertCountEquals(1)
        capture("cached-info")
    }

    @Test
    fun galleryGifCanPlayPauseAndZoom() {
        org.junit.Assume.assumeTrue(
            InstrumentationRegistry.getArguments().getString("expectOffline") != "true"
        )
        ready("home_screen")
        open(373)
        selectTab(2)
        ui.onNodeWithTag("character_detail").performScrollToKey("gallery")
        // 分类栏是横向滚动容器；先把官方图集滚入可视区，避免点击被裁剪的语义节点。
        repeat(3) { ui.onNodeWithTag("gallery_groups").performTouchInput { swipeLeft() } }
        ui.onNodeWithTag("gallery_group_3").assertIsDisplayed().performClick()
        ui.onNodeWithText("展开全部").performClick()
        capture("gallery-expanded")
        ui.onNodeWithTag("gallery_image_3_6").performScrollTo().performSemanticsAction(
            SemanticsActions.OnClick
        ) {
            it()
        }
        ui.waitUntil(40_000) { ui.onAllNodesWithText("暂停动图").fetchSemanticsNodes().isNotEmpty() }
        capture("gallery-gif")
        ui.onNodeWithText("暂停动图").performClick()
        ui.onNodeWithText("播放动图").assertExists()
        ui.onNodeWithContentDescription("放大").performClick()
        capture("gallery-gif-zoom")
        ui.onNodeWithContentDescription("关闭图片").performClick()
        ready("character_detail")
    }

    @Test
    fun voicePlaybackStopsWhenSwitchingCategory() {
        if (InstrumentationRegistry.getArguments().getString("expectOffline") == "true") return
        ready("home_screen")
        open(86)
        selectTab(3)
        ui.onNodeWithTag("character_detail").performScrollToIndex(2)
        ui.onAllNodesWithContentDescription("播放语音").onFirst().performClick()
        ui.waitUntil(30_000) {
            ui.onAllNodesWithContentDescription("暂停").fetchSemanticsNodes().isNotEmpty()
        }
        capture("voice-playing")
        selectTab(0)
        ui.onAllNodesWithContentDescription("暂停").assertCountEquals(0)
    }
}
