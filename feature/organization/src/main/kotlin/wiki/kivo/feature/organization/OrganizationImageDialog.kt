package wiki.kivo.feature.organization

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import java.io.File
import kotlinx.coroutines.CancellationException
import wiki.kivo.core.designsystem.LocalKivoSettings
import wiki.kivo.core.media.*

/** 复用角色模块的区域解码器和统一磁盘预算，大尺寸地图/正文图片不整张展开为原尺寸位图。 */
@Composable
internal fun OrganizationImageDialog(url: String, assets: CharacterAssets, close: () -> Unit) {
    var file by remember(url) { mutableStateOf<File?>(null) }
    var error by remember(url) { mutableStateOf<String?>(null) }
    var retry by remember(url) { mutableIntStateOf(0) }
    var view by remember { mutableStateOf<ArchiveImageView?>(null) }
    var active by remember { mutableStateOf(true) }
    var animated by remember(url) { mutableStateOf(false) }
    var playing by remember(url) { mutableStateOf(false) }
    val settings = LocalKivoSettings.current
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        active = false
        view?.active = false
    }
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        active = true
        view?.active = true
    }
    LaunchedEffect(url, retry) {
        error = null
        try {
            file = assets.fetch(url)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = failure.message ?: "图片暂时无法加载"
        }
    }
    Dialog(close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.safeDrawingPadding()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton({ view?.zoomBy(1.5f) }) { Text("放大") }
                    TextButton({ view?.resetCamera() }) { Text("复位") }
                    Spacer(Modifier.weight(1f))
                    IconButton(close) { Icon(Icons.Outlined.Close, "关闭图片") }
                }
                if (animated)
                    TextButton({ playing = !playing }) { Text(if (playing) "暂停动图" else "播放动图") }
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    file?.let { source ->
                        key(url, retry) {
                            AndroidView(
                                factory = { context ->
                                    ArchiveImageView(context).also {
                                        view = it
                                        it.onReady = { value -> animated = value }
                                        it.onFailure = { message -> error = message }
                                        it.load(source)
                                    }
                                },
                                modifier = Modifier.fillMaxSize(),
                                update = {
                                    it.active = active
                                    it.playing = playing && !settings.reducedMotion
                                },
                            )
                        }
                    }
                    if (file == null && error == null) CircularProgressIndicator()
                    error?.let { message ->
                        Column(Modifier.padding(24.dp)) {
                            Text(message)
                            TextButton({ retry++ }) { Text("重试图片") }
                        }
                    }
                }
            }
        }
    }
}
