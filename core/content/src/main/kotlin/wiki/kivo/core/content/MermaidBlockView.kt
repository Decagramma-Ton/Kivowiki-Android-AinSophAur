package wiki.kivo.core.content

import android.annotation.SuppressLint
import android.webkit.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.io.ByteArrayInputStream
import kotlinx.coroutines.delay

@Composable
internal fun MermaidBlockView(source: String) {
    var expanded by rememberSaveable(source) { mutableStateOf(false) }
    var code by rememberSaveable(source) { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().testTag("mermaid_block"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("图表", style = MaterialTheme.typography.titleSmall)
        if (source.length <= 32000 && !expanded)
            MermaidCanvas(source, false, Modifier.fillMaxWidth().height(360.dp))
        else if (source.length > 32000) Text("图表较大，请展开源码阅读。")
        Row {
            TextButton({ expanded = true }, enabled = source.length <= 32000) { Text("放大图表") }
            TextButton({ code = !code }) { Text(if (code) "收起源码" else "查看源码") }
        }
        if (code) SelectionContainer { ContentBlockView(ContentBlock.Code(source), {}, {}) }
    }
    if (expanded)
        Dialog(
            { expanded = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(Modifier.fillMaxSize()) {
                Column(Modifier.safeDrawingPadding().padding(12.dp)) {
                    Row {
                        Text(
                            "双指缩放、拖动查看",
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        TextButton({ expanded = false }) { Text("关闭图表") }
                    }
                    MermaidCanvas(source, true, Modifier.fillMaxWidth().weight(1f))
                }
            }
        }
}

/**
 * WebView 仅承载单个图表：运行时随签名 APK 提供，无账号 Cookie、JS bridge 或远程脚本。 拦截器只允许一个固定本地资源，其他网络/文件请求全部拒绝。离开可见区立即销毁。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun MermaidCanvas(source: String, expanded: Boolean, modifier: Modifier) {
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    val html = remember(source, dark, expanded) { MermaidDocument.html(source, dark, expanded) }
    var web by remember { mutableStateOf<WebView?>(null) }
    var status by remember(html) { mutableStateOf("正在绘制图表…") }
    var rendererFailed by remember(html) { mutableStateOf(false) }
    LaunchedEffect(web, html) {
        val target = web ?: return@LaunchedEffect
        repeat(100) {
            var state = ""
            target.evaluateJavascript("window.kivoDiagramState || 'loading'") { state = it }
            delay(150)
            if (state == "\"ready\"") {
                status = "图表已绘制"
                return@LaunchedEffect
            }
            if (state == "\"error\"") {
                status = "图表暂时无法绘制，请查看源码"
                return@LaunchedEffect
            }
        }
        status = "图表绘制超时，请查看源码或重新打开"
        target.stopLoading()
    }
    Column(modifier) {
        Text(
            status,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.testTag("mermaid_status"),
        )
        if (!rendererFailed)
            key(html) {
                AndroidView(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    factory = { context ->
                        WebView(context).apply {
                            settings.javaScriptEnabled = true
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            settings.domStorageEnabled = false
                            settings.blockNetworkLoads = true
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            settings.setSupportZoom(expanded)
                            settings.builtInZoomControls = expanded
                            settings.displayZoomControls = false
                            isVerticalScrollBarEnabled = expanded
                            isHorizontalScrollBarEnabled = expanded
                            webViewClient =
                                object : WebViewClient() {
                                    override fun onRenderProcessGone(
                                        view: WebView,
                                        detail: RenderProcessGoneDetail,
                                    ): Boolean {
                                        // 系统回收单个图表的渲染进程时，正文页面仍可继续阅读。
                                        if (web === view) {
                                            rendererFailed = true
                                            web = null
                                            status = "图表引擎已关闭，请重新打开图表或查看源码"
                                        }
                                        return true
                                    }

                                    override fun shouldOverrideUrlLoading(
                                        view: WebView,
                                        request: WebResourceRequest,
                                    ) = true

                                    override fun shouldInterceptRequest(
                                        view: WebView,
                                        request: WebResourceRequest,
                                    ): WebResourceResponse {
                                        return if (
                                            request.url.toString() ==
                                                "https://appassets.androidplatform.net/mermaid/mermaid.min.js"
                                        )
                                            WebResourceResponse(
                                                "application/javascript",
                                                "UTF-8",
                                                context.assets.open("mermaid/mermaid.min.js"),
                                            )
                                        else
                                            WebResourceResponse(
                                                "text/plain",
                                                "UTF-8",
                                                ByteArrayInputStream(ByteArray(0)),
                                            )
                                    }
                                }
                            loadDataWithBaseURL(
                                "https://appassets.androidplatform.net/mermaid/",
                                html,
                                "text/html",
                                "UTF-8",
                                null,
                            )
                            web = this
                        }
                    },
                    onRelease = { view ->
                        if (web === view) web = null
                        if (!rendererFailed) {
                            view.stopLoading()
                            view.loadUrl("about:blank")
                        }
                        view.destroy()
                    },
                )
            }
    }
}
