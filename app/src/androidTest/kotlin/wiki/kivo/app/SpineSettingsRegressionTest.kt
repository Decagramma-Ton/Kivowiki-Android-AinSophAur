package wiki.kivo.app

import android.graphics.Bitmap
import android.graphics.Color
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import wiki.kivo.core.data.local.SettingsRepository
import wiki.kivo.core.media.CharacterAssets
import wiki.kivo.core.media.PreparedCharacterMedia
import wiki.kivo.core.media.SpinePreviewView
import wiki.kivo.core.model.CacheLimit
import wiki.kivo.core.model.CharacterMedia

/** 验证真实原生帧，不只检查设置开关是否出现在界面上；所有网络请求均为公开读取。 */
@RunWith(AndroidJUnit4::class)
class SpineSettingsRegressionTest {
    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun settingsRoundTripPreservesExplicitOptOut() = runBlocking {
        val settings = SettingsRepository(context)
        val original = settings.settings.first()
        try {
            settings.setFlag("images", false)
            settings.setFlag("external_images", false)
            settings.setFlag("spine_gpu", false)
            settings.setFlag("spine_fix_blend", false)
            settings.setFlag("spine_experimental", true)
            settings.setCacheLimit(CacheLimit.UNLIMITED)
            val reopened = SettingsRepository(context).settings.first()
            assertFalse(reopened.loadImages)
            assertFalse(reopened.externalImages)
            assertFalse(reopened.spineGpu)
            assertFalse(reopened.spineFixBlend)
            assertTrue(reopened.spineExperimental)
            assertEquals(CacheLimit.UNLIMITED, reopened.cacheLimit)
        } finally {
            settings.setFlag("images", original.loadImages)
            settings.setFlag("external_images", original.externalImages)
            settings.setFlag("spine_gpu", original.spineGpu)
            settings.setFlag("spine_fix_blend", original.spineFixBlend)
            settings.setFlag("spine_experimental", original.spineExperimental)
            settings.setCacheLimit(original.cacheLimit)
        }
    }

    @Test
    fun dressKayokoOriginalLobbyKeepsCameraAndRestoresLighting() = runBlocking {
        val client = OkHttpClient()
        val data =
            client
                .newCall(
                    Request.Builder().url("https://api.kivo.wiki/api/v1/data/spines/682").build()
                )
                .execute()
                .use {
                    check(it.isSuccessful)
                    JSONObject(requireNotNull(it.body).string()).getJSONObject("data")
                }
        fun url(value: String) = if (value.startsWith("//")) "https:$value" else value
        val images = data.getJSONArray("images")
        val media =
            CharacterMedia(
                682,
                data.getString("name"),
                "礼服佳代子原始大厅",
                "home",
                url(data.getString("skel_file")),
                url(data.getString("atlas_file")),
                List(images.length()) { url(images.getString(it)) },
            )
        val assets = CharacterAssets(context, client)
        val prepared = assets.prepare(media)
        try {
            verifyFrames(prepared)
        } finally {
            assets.release(prepared)
        }
    }

    private fun verifyFrames(prepared: PreparedCharacterMedia) {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val ready = CountDownLatch(1)
            var failure: String? = null
            var names = emptyList<String>()
            lateinit var view: SpinePreviewView
            lateinit var host: FrameLayout
            scenario.onActivity { activity ->
                host = FrameLayout(activity)
                activity.addContentView(host, FrameLayout.LayoutParams(360, 480))
                view =
                    SpinePreviewView(activity).apply {
                        playing = false
                        background = Color.rgb(70, 80, 90)
                        onReady = { animations, _ ->
                            names = animations
                            ready.countDown()
                        }
                        onFailure = {
                            failure = it
                            ready.countDown()
                        }
                    }
                host.addView(view, FrameLayout.LayoutParams(360, 480))
                view.load(prepared)
            }
            assertTrue("素材解析超时", ready.await(60, TimeUnit.SECONDS))
            assertNull(failure)
            scenario.onActivity {
                view.layout(0, 0, 360, 480)
                val initial = names.firstOrNull { it.equals("Idle_01", true) } ?: names.first()
                view.animation(initial)
                view.zoomBy(1.8f)
                // 缩放和平移同时覆盖；切换到另一动作再切回，画面必须逐像素一致。
                val now = android.os.SystemClock.uptimeMillis()
                listOf(
                        Triple(MotionEvent.ACTION_DOWN, 100f, 100f),
                        Triple(MotionEvent.ACTION_MOVE, 126f, 118f),
                        Triple(MotionEvent.ACTION_UP, 126f, 118f),
                    )
                    .forEach { (action, x, y) ->
                        MotionEvent.obtain(now, now, action, x, y, 0).also { event ->
                            view.onTouchEvent(event)
                            event.recycle()
                        }
                    }
                val before = view.snapshot(360)
                names.firstOrNull { it != initial }?.let(view::animation)
                view.animation(initial)
                val after = view.snapshot(360)
                assertTrue("切换动作不得改变缩放或平移", before.sameAs(after))
                before.recycle()
                after.recycle()
                view.resetCamera()
                val fixed = view.snapshot(360)
                view.fixBlendMode = false
                val original = view.snapshot(360)
                assertFalse("礼服佳代子原始大厅应实际应用光效混合修复", fixed.sameAs(original))
                view.fixBlendMode = true
                val restored = view.snapshot(360)
                assertTrue("重新开启修复应恢复相同帧", fixed.sameAs(restored))
                val output =
                    File(context.getExternalFilesDir(null), "settings-spine-regression").apply {
                        mkdirs()
                    }
                listOf("kayoko-fixed" to fixed, "kayoko-original" to original).forEach {
                    (name, bitmap) ->
                    File(output, "$name.png").outputStream().use {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                }
                fixed.recycle()
                original.recycle()
                restored.recycle()
                view.gpuRendering = false
                assertEquals(android.view.View.LAYER_TYPE_SOFTWARE, view.layerType)
                view.gpuRendering = true
                if (android.os.Build.VERSION.SDK_INT >= 29)
                    assertEquals(android.view.View.LAYER_TYPE_NONE, view.layerType)
                host.removeView(view)
            }
        }
    }
}
