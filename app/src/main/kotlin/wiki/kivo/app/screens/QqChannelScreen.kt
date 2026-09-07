package wiki.kivo.app.screens

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import android.webkit.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import wiki.kivo.core.designsystem.StatusNote

private const val CHANNEL = "https://pd.qq.com/g/Nekuso0721"

/** 仅用于用户指定的 QQ 频道；不注入 App 会话、JavaScript 桥或文件访问能力。 */
@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
fun QqChannelScreen() {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var progress by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var lastGesture by remember { mutableLongStateOf(0L) }
    fun external(uri: Uri, packageName: String? = null) {
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, uri).apply {
                    addCategory(Intent.CATEGORY_BROWSABLE)
                    if (packageName != null) setPackage(packageName)
                }
            )
            error = null
        } catch (_: ActivityNotFoundException) {
            error = "没有找到可打开此链接的应用。加入频道需要安装 QQ。"
        } catch (_: SecurityException) {
            error = "系统暂时无法打开此链接，请稍后重试。"
        }
    }
    val web = remember {
        WebView(context).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = false
                allowContentAccess = false
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                setSupportMultipleWindows(false)
                javaScriptCanOpenWindowsAutomatically = false
                mediaPlaybackRequiresUserGesture = true
            }
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
            setOnTouchListener { _, event ->
                if (event.action == android.view.MotionEvent.ACTION_UP)
                    lastGesture = SystemClock.elapsedRealtime()
                false
            }
            webChromeClient =
                object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        progress = newProgress
                    }
                }
            webViewClient =
                object : WebViewClient() {
                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        error = null
                    }

                    override fun onPageFinished(view: WebView, url: String?) {
                        canGoBack = view.canGoBack()
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest,
                        failure: WebResourceError?,
                    ) {
                        if (request.isForMainFrame) error = "频道暂时未能加载，请检查网络后重试。"
                    }

                    override fun onReceivedHttpError(
                        view: WebView?,
                        request: WebResourceRequest,
                        response: WebResourceResponse?,
                    ) {
                        if (request.isForMainFrame) error = "频道服务暂时不可用，请稍后重试。"
                    }

                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest,
                    ): Boolean {
                        val uri = request.url
                        val scheme = uri.scheme?.lowercase()
                        val host = uri.host?.lowercase().orEmpty()
                        if (
                            scheme == "https" &&
                                (host == "qq.com" || host.endsWith(".qq.com")) &&
                                uri.userInfo == null
                        )
                            return false
                        if (!request.isForMainFrame) return true
                        val userActivated =
                            request.hasGesture() ||
                                SystemClock.elapsedRealtime() - lastGesture < 5000
                        if (!userActivated) return true
                        if (scheme == "https" && uri.userInfo == null) {
                            external(uri)
                            return true
                        }
                        // 加入按钮可能先运行网页脚本再唤起 QQ，短时保留用户手势，避免误拦截这一正常流程。
                        if (scheme in setOf("mqqapi", "mqq", "mqqopensdk", "timapi")) {
                            external(uri)
                            return true
                        }
                        if (scheme == "intent") {
                            val parsed = runCatching {
                                Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME)
                            }.getOrNull()
                            val target = parsed?.data
                            if (
                                target?.scheme in setOf("mqqapi", "mqq", "mqqopensdk", "timapi") &&
                                    parsed?.`package` in
                                        setOf(null, "com.tencent.mobileqq", "com.tencent.tim")
                            ) {
                                external(target!!, parsed?.`package`)
                            } else error = "暂不支持此频道跳转链接。"
                            return true
                        }
                        error = "暂不支持此链接类型。"
                        return true
                    }
                }
            loadUrl(CHANNEL)
        }
    }
    DisposableEffect(web, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> web.onResume()
                Lifecycle.Event.ON_PAUSE -> web.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            web.stopLoading()
            web.webChromeClient = null
            web.destroy()
        }
    }
    BackHandler(canGoBack) {
        web.goBack()
        canGoBack = web.canGoBack()
    }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = { web.loadUrl(CHANNEL) }) { Text("频道首页") }
            TextButton(
                onClick = {
                    error = null
                    web.reload()
                }
            ) {
                Text("刷新")
            }
            TextButton(onClick = { external(Uri.parse(CHANNEL)) }) { Text("浏览器打开") }
        }
        if (progress < 100)
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier.fillMaxWidth(),
            )
        error?.let { Box(Modifier.padding(16.dp)) { StatusNote(it) } }
        AndroidView(factory = { web }, modifier = Modifier.fillMaxSize())
    }
}
