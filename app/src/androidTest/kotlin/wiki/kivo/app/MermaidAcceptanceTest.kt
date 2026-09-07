package wiki.kivo.app

import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.webkit.WebView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import wiki.kivo.core.content.*
import wiki.kivo.core.designsystem.KivoTheme
import wiki.kivo.core.model.AppSettings

class MermaidAcceptanceTest {
    @get:Rule val ui = createComposeRule()

    @Test
    fun shirokoDiagramRendersRealNodesAndEdgesWithoutNetwork() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val source =
            instrumentation.context.assets.open("shiroko-mermaid.mmd").bufferedReader().use {
                it.readText()
            }
        ui.setContent {
            KivoTheme(AppSettings()) {
                Column(Modifier.safeDrawingPadding()) {
                    ContentBlockView(ContentBlock.Mermaid(source), {}, {})
                }
            }
        }
        ui.waitUntil(25000) { ui.onAllNodesWithText("图表已绘制").fetchSemanticsNodes().isNotEmpty() }
        fun descendants(view: View): List<View> =
            listOf(view) +
                if (view is ViewGroup)
                    (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) }
                else emptyList()
        lateinit var web: WebView
        ui.runOnIdle {
            web =
                WindowInspector.getGlobalWindowViews()
                    .flatMap { descendants(it) }
                    .filterIsInstance<WebView>()
                    .single()
        }
        var proof = ""
        ui.runOnUiThread {
            assertTrue(web.settings.blockNetworkLoads)
            assertFalse(web.settings.allowFileAccess)
            assertFalse(web.settings.allowContentAccess)
            web.evaluateJavascript(
                "JSON.stringify({nodes:document.querySelectorAll('g.node').length,edges:document.querySelectorAll('.flowchart-link').length,text:document.getElementById('diagram').textContent})"
            ) {
                proof = it
            }
        }
        ui.waitUntil(5000) { proof.isNotBlank() }
        assertTrue(proof, proof.contains("nodes\\\":9"))
        assertTrue(proof, proof.contains("edges\\\":13"))
        assertTrue(proof, proof.contains("被星野收留"))
        val dir =
            File(instrumentation.targetContext.getExternalFilesDir(null), "organization-acceptance")
                .apply { mkdirs() }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(dir, "mermaid-shiroko.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
        ui.onNodeWithText("放大图表").performClick()
        ui.waitUntil(25000) { ui.onAllNodesWithText("图表已绘制").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("关闭图表").performClick()
    }
}
