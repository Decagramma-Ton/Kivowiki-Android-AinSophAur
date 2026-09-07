package wiki.kivo.feature.character

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.*
import androidx.media3.ui.PlayerView
import java.io.File
import kotlinx.coroutines.*
import wiki.kivo.core.designsystem.LocalKivoSettings
import wiki.kivo.core.media.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CharacterImageViewer(
    initial: String,
    gallery: List<String>,
    assets: CharacterAssets,
    close: () -> Unit,
) {
    val urls = remember(initial, gallery) { if (initial in gallery) gallery else listOf(initial) }
    var index by
        rememberSaveable(initial) { mutableIntStateOf(urls.indexOf(initial).coerceAtLeast(0)) }
    val url = urls[index.coerceIn(urls.indices)]
    var file by remember(url) { mutableStateOf<File?>(null) }
    var error by remember(url) { mutableStateOf<String?>(null) }
    var retry by remember(url) { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var view by remember { mutableStateOf<ArchiveImageView?>(null) }
    val reducedMotion = LocalKivoSettings.current.reducedMotion
    var animated by remember(url) { mutableStateOf(false) }
    var playing by remember(url) { mutableStateOf(!reducedMotion) }
    var active by remember { mutableStateOf(true) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        active = false
        view?.active = false
    }
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        active = true
        view?.active = true
    }
    val extension =
        remember(url) {
            runCatching { CharacterAssets.basename(url).substringAfterLast('.', "png") }
                .getOrDefault("png")
        }
    val mime =
        when (extension.lowercase()) {
            "jpg",
            "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            else -> "image/png"
        }
    val save =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(mime)) { uri ->
            val source = file
            if (uri != null && source != null)
                scope.launch {
                    busy = true
                    try {
                        CharacterExport.copy(context.contentResolver, uri, source)
                        message = "原始图片已保存"
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        message = "保存失败：${e.message}"
                    } finally {
                        busy = false
                    }
                }
        }
    LaunchedEffect(url, retry) {
        try {
            file = assets.fetch(url)
            error = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "图片下载失败"
        }
    }
    Dialog(
        close,
        DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val dialogView = LocalView.current
        DisposableEffect(dialogView) {
            // 图片查看器始终使用深色背景；仅调整当前弹窗，关闭后不污染主界面主题。
            val window = (dialogView.parent as? DialogWindowProvider)?.window
            val controller = window?.let { WindowCompat.getInsetsController(it, dialogView) }
            val lightStatus = controller?.isAppearanceLightStatusBars
            val lightNavigation = controller?.isAppearanceLightNavigationBars
            controller?.isAppearanceLightStatusBars = false
            controller?.isAppearanceLightNavigationBars = false
            onDispose {
                lightStatus?.let { controller.isAppearanceLightStatusBars = it }
                lightNavigation?.let { controller.isAppearanceLightNavigationBars = it }
            }
        }
        Surface(Modifier.fillMaxSize(), color = Color(0xff11151c), contentColor = Color.White) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                TopAppBar(
                    title = { Text("${index+1} / ${urls.size}") },
                    navigationIcon = { IconButton(close) { Icon(Icons.Outlined.Close, "关闭图片") } },
                    actions = {
                        IconButton(
                            { save.launch("kivo-${System.currentTimeMillis()}.$extension") },
                            enabled = file != null && !busy,
                        ) {
                            Icon(Icons.Outlined.Download, "保存原图")
                        }
                    },
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = Color(0xff11151c),
                            titleContentColor = Color.White,
                            navigationIconContentColor = Color.White,
                            actionIconContentColor = Color.White,
                        ),
                )
                Box(
                    Modifier.weight(1f).fillMaxWidth().clipToBounds(),
                    contentAlignment = Alignment.Center,
                ) {
                    file?.let { source ->
                        key(url, retry) {
                            AndroidView(
                                factory = { ctx ->
                                    ArchiveImageView(ctx).also {
                                        view = it
                                        it.onFailure = { error = it }
                                        it.onReady = { animated = it }
                                        it.load(source)
                                    }
                                },
                                modifier = Modifier.fillMaxSize(),
                                update = {
                                    it.playing = playing
                                    it.active = active
                                },
                            )
                        }
                    }
                    if (file == null && error == null) CircularProgressIndicator()
                    error?.let {
                        Column(
                            Modifier.padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(it)
                            Button({ retry++ }) { Text("重试") }
                        }
                    }
                }
                message?.let {
                    Text(
                        it,
                        Modifier.padding(horizontal = 16.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (animated)
                    TextButton(
                        { playing = !playing },
                        Modifier.align(Alignment.CenterHorizontally),
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                    ) {
                        Text(if (playing) "暂停动图" else "播放动图")
                    }
                Row(
                    Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    IconButton({ index-- }, enabled = index > 0) {
                        Icon(Icons.Outlined.ChevronLeft, "上一张")
                    }
                    IconButton({ view?.zoomBy(.67f) }) { Icon(Icons.Outlined.ZoomOut, "缩小") }
                    TextButton(
                        { view?.resetCamera() },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                    ) {
                        Text("复位")
                    }
                    IconButton({ view?.zoomBy(1.5f) }) { Icon(Icons.Outlined.ZoomIn, "放大") }
                    IconButton({ index++ }, enabled = index < urls.lastIndex) {
                        Icon(Icons.Outlined.ChevronRight, "下一张")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CharacterVideoDialog(url: String, playback: CharacterPlayback, close: () -> Unit) {
    val token = remember { Any() }
    val player = remember { playback.acquire(token) }
    val status by playback.status.collectAsStateWithLifecycle()
    LaunchedEffect(url) { playback.play(token, url) }
    DisposableEffect(Unit) { onDispose { playback.release(token) } }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { playback.pause(token) }
    Dialog(
        close,
        DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.safeDrawingPadding()) {
                TopAppBar(
                    title = { Text("技能演示") },
                    navigationIcon = { IconButton(close) { Icon(Icons.Outlined.Close, "关闭视频") } },
                )
                AndroidView(
                    factory = { context ->
                        PlayerView(context).apply {
                            this.player = player
                            useController = true
                        }
                    },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    onRelease = { it.player = null },
                )
                status.error?.let {
                    Text(it, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
                    TextButton({ playback.play(token, url) }) { Text("重试播放") }
                }
            }
        }
    }
}
