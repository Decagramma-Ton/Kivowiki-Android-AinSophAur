package wiki.kivo.feature.character

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import wiki.kivo.core.designsystem.*
import wiki.kivo.core.media.*
import wiki.kivo.core.model.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CharacterMediaDialog(
    id: Int,
    spine: Boolean,
    vm: CharacterViewModel,
    close: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = LocalKivoSettings.current
    var metadata by remember { mutableStateOf<CharacterMedia?>(null) }
    var prepared by remember { mutableStateOf<PreparedCharacterMedia?>(null) }
    DisposableEffect(prepared) {
        val held = prepared
        onDispose { held?.let(vm.assets::release) }
    }
    var scene by remember { mutableStateOf<GltfPackage?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var progress by remember { mutableStateOf(AssetProgress()) }
    var retry by remember { mutableIntStateOf(0) }
    var spineView by remember { mutableStateOf<SpinePreviewView?>(null) }
    var modelView by remember { mutableStateOf<ModelPreviewView?>(null) }
    var animations by remember { mutableStateOf<List<String>>(emptyList()) }
    var skins by remember { mutableStateOf<List<String>>(emptyList()) }
    var selected by rememberSaveable { mutableStateOf("") }
    var skin by rememberSaveable { mutableStateOf("") }
    var playing by rememberSaveable { mutableStateOf(!settings.reducedMotion) }
    var visible by remember { mutableStateOf(true) }
    var fill by rememberSaveable { mutableStateOf(false) }
    var lowPower by rememberSaveable { mutableStateOf(settings.reducedMotion) }
    var rotate by rememberSaveable { mutableStateOf(false) }
    var bg by rememberSaveable { mutableIntStateOf(0) }
    var style by rememberSaveable { mutableIntStateOf(0) }
    var mouth by rememberSaveable { mutableIntStateOf(60) }
    var hasMouth by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf<String?>(null) }
    var controls by rememberSaveable { mutableStateOf(true) }
    var exporting by remember { mutableStateOf(false) }
    var exportProgress by remember { mutableFloatStateOf(0f) }
    var exportJob by remember { mutableStateOf<Job?>(null) }
    var fullExport by rememberSaveable { mutableStateOf(true) }
    var exportEdge by rememberSaveable { mutableIntStateOf(1920) }
    var exportSeconds by rememberSaveable { mutableIntStateOf(5) }
    val panelScroll = rememberScrollState()
    val png =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri
            ->
            if (uri != null)
                exportJob = scope.launch {
                    exporting = true
                    try {
                        val bitmap =
                            if (spine) {
                                if (fullExport) requireNotNull(spineView).fullFrameSource()()
                                else requireNotNull(spineView).snapshot(Int.MAX_VALUE)
                            } else {
                                if (fullExport) requireNotNull(modelView).fullSnapshot(2048)
                                else requireNotNull(modelView).snapshot(Int.MAX_VALUE)
                            }
                        CharacterExport.png(context.contentResolver, uri, bitmap)
                        message = "画面已保存"
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        message = "保存失败：${e.message}"
                    } finally {
                        modelView?.finishExport()
                        exporting = false
                    }
                }
        }
    val mp4 =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("video/mp4")) { uri
            ->
            if (uri != null)
                exportJob = scope.launch {
                    exporting = true
                    exportProgress = 0f
                    val previous = playing
                    playing = !fullExport
                    if (fullExport) {
                        spineView?.playing = false
                        modelView?.playing = false
                        modelView?.active = false
                    }
                    val temp = File(context.cacheDir, "character-export-${System.nanoTime()}.mp4")
                    try {
                        val fullSpine =
                            if (spine && fullExport)
                                requireNotNull(spineView).fullFrameSource(exportEdge, exportSeconds)
                            else null
                        var frame = 0
                        CharacterExport.video(
                            temp,
                            exportSeconds,
                            {
                                if (spine)
                                    fullSpine?.invoke()
                                        ?: requireNotNull(spineView).snapshot(exportEdge)
                                else if (fullExport)
                                    requireNotNull(modelView)
                                        .fullSnapshot(exportEdge, frame++ / 24f)
                                else requireNotNull(modelView).snapshot(exportEdge)
                            },
                        ) {
                            exportProgress = it
                        }
                        CharacterExport.copy(context.contentResolver, uri, temp)
                        message = "无声视频已保存 · ${CharacterExport.videoDimensions(temp)}"
                    } catch (e: CancellationException) {
                        message = "导出已取消"
                        throw e
                    } catch (e: Exception) {
                        message = "视频导出失败：${e.message}"
                    } finally {
                        temp.delete()
                        modelView?.finishExport()
                        playing = previous
                        spineView?.playing = previous
                        modelView?.playing = previous
                        modelView?.active = visible
                        exporting = false
                    }
                }
        }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        visible = false
        // ON_STOP 后 Compose 可能暂停重组，必须同步停止原生调度，不能等待 AndroidView.update。
        spineView?.active = false
        modelView?.active = false
        exportJob?.cancel()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        visible = true
        spineView?.active = true
        modelView?.active = true
    }
    val network by NetworkRecovery.changes.collectAsState()
    var previousNetwork by remember { mutableLongStateOf(network) }
    LaunchedEffect(network) {
        if (network != previousNetwork && error != null && !loading && prepared == null) retry++
        previousNetwork = network
    }
    LaunchedEffect(id, retry) {
        loading = true
        error = null
        try {
            val info = vm.repository.media(id, spine).first { !it.loading }
            val media = info.value ?: error(info.error ?: "素材元数据暂不可用")
            metadata = media
            // 下载失败保留已校验的依赖；仅渲染失败的手动重试才清理完整包。
            if (retry > 0 && prepared != null) vm.assets.invalidate(media)
            prepared = vm.assets.prepare(media) { next -> scope.launch { progress = next } }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "素材加载失败"
            loading = false
        }
    }
    LaunchedEffect(prepared, style, mouth) {
        val value = prepared ?: return@LaunchedEffect
        if (!spine) {
            loading = true
            error = null
            try {
                scene = vm.assets.model(value, style, mouth)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "模型读取失败"
                loading = false
            }
        }
    }
    val colors =
        listOf(
            0xffedf1f7.toInt(),
            0xff202530.toInt(),
            0xff4b7f66.toInt(),
            android.graphics.Color.TRANSPARENT,
        )
    Dialog(
        close,
        DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                TopAppBar(
                    title = {
                        Text(
                            metadata?.label ?: "动态素材 #$id",
                            maxLines = 2,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    },
                    navigationIcon = { IconButton(close) { Icon(Icons.Outlined.Close, "关闭预览") } },
                    actions = {
                        IconButton({ controls = !controls }) {
                            Icon(
                                if (controls) Icons.Outlined.Fullscreen
                                else Icons.Outlined.FullscreenExit,
                                if (controls) "隐藏控制面板" else "显示控制面板",
                            )
                        }
                    },
                )
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val wide = maxWidth >= 760.dp
                    val canvas: @Composable (Modifier) -> Unit = { modifier ->
                        Box(
                            modifier.clipToBounds().background(Color(colors[bg])),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (spine && prepared != null)
                                key(retry) {
                                    AndroidView(
                                        factory = { ctx ->
                                            SpinePreviewView(ctx).also { view ->
                                                view.gpuRendering = settings.spineGpu
                                                view.fixBlendMode = settings.spineFixBlend
                                                view.experimentalRendering =
                                                    settings.spineExperimental
                                                spineView = view
                                                view.onReady = { a, s ->
                                                    loading = false
                                                    animations = a
                                                    skins = s
                                                    selected =
                                                        a.firstOrNull {
                                                            it.equals("Idle_01", true) ||
                                                                it.equals("Idle", true)
                                                        } ?: a.firstOrNull().orEmpty()
                                                    skin = s.firstOrNull().orEmpty()
                                                }
                                                view.onFailure = {
                                                    error = it
                                                    loading = false
                                                }
                                                view.load(requireNotNull(prepared))
                                            }
                                        },
                                        modifier = Modifier.fillMaxSize(),
                                        update = {
                                            it.active = visible
                                            it.playing = playing
                                            it.lowPower = lowPower
                                            it.fill = fill
                                            it.background = colors[bg]
                                            it.gpuRendering = settings.spineGpu
                                            it.fixBlendMode = settings.spineFixBlend
                                            it.experimentalRendering = settings.spineExperimental
                                        },
                                    )
                                }
                            if (!spine && scene != null)
                                key(retry) {
                                    AndroidView(
                                        factory = { ctx ->
                                            ModelPreviewView(ctx).also { view ->
                                                modelView = view
                                                view.onReady = { a, m ->
                                                    loading = false
                                                    animations = a
                                                    hasMouth = m
                                                    if (selected.isBlank())
                                                        selected =
                                                            a.firstOrNull {
                                                                it.endsWith("Normal_Idle", true)
                                                            }
                                                                ?: a.firstOrNull {
                                                                    it.contains("Idle", true)
                                                                }
                                                                ?: a.firstOrNull().orEmpty()
                                                }
                                                view.onFailure = {
                                                    error = it
                                                    loading = false
                                                }
                                                view.load(requireNotNull(scene))
                                            }
                                        },
                                        modifier = Modifier.fillMaxSize(),
                                        update = {
                                            it.active = visible
                                            it.playing = playing
                                            it.lowPower = lowPower
                                            it.rotate = rotate
                                            it.background = colors[bg]
                                            it.load(requireNotNull(scene))
                                        },
                                    )
                                }
                            if (loading)
                                Surface(shape = MaterialTheme.shapes.large, tonalElevation = 4.dp) {
                                    Column(
                                        Modifier.padding(20.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        CircularProgressIndicator()
                                        Text(
                                            if (progress.completed > 0)
                                                "已读取 ${progress.completed} 个资源 · ${"%.1f".format(progress.bytes/1048576.0)} MiB"
                                            else "正在准备预览…"
                                        )
                                    }
                                }
                            error?.let {
                                Surface(shape = MaterialTheme.shapes.large) {
                                    Column(
                                        Modifier.padding(20.dp).widthIn(max = 400.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        Text(it, color = MaterialTheme.colorScheme.error)
                                        Button({
                                            prepared = null
                                            scene = null
                                            retry++
                                        }) {
                                            Text("重新下载并重试")
                                        }
                                    }
                                }
                            }
                        }
                    }
                    fun selectAnimation(index: Int) {
                        if (animations.isEmpty()) return
                        val next = index.mod(animations.size)
                        selected = animations[next]
                        spineView?.animation(selected)
                        modelView?.animation(next)
                    }
                    fun stepAnimation(delta: Int) {
                        val current = animations.indexOf(selected).takeIf { it >= 0 } ?: 0
                        selectAnimation(current + delta)
                    }
                    fun stepMouth(delta: Int) {
                        mouth = (mouth + delta).mod(64)
                    }
                    val panel: @Composable (Modifier) -> Unit = { modifier ->
                        Column(
                            modifier.verticalScroll(panelScroll).padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilledTonalButton(
                                    { playing = !playing },
                                    Modifier.weight(1f),
                                    enabled = !exporting,
                                ) {
                                    Icon(
                                        if (playing) Icons.Outlined.Pause
                                        else Icons.Outlined.PlayArrow,
                                        null,
                                    )
                                    Text(if (playing) "暂停" else "播放")
                                }
                                OutlinedButton(
                                    {
                                        spineView?.resetCamera()
                                        modelView?.resetCamera()
                                    },
                                    Modifier.weight(1f),
                                ) {
                                    Text("镜头复位")
                                }
                            }
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                            ) {
                                IconButton({
                                    spineView?.zoomBy(1.25f)
                                    modelView?.zoomBy(-.25f)
                                }) {
                                    Icon(Icons.Outlined.ZoomIn, "放大")
                                }
                                IconButton({
                                    spineView?.zoomBy(.8f)
                                    modelView?.zoomBy(.25f)
                                }) {
                                    Icon(Icons.Outlined.ZoomOut, "缩小")
                                }
                                if (!spine) {
                                    IconButton({ modelView?.turnBy(-60) }) {
                                        Icon(Icons.Outlined.RotateLeft, "向左旋转")
                                    }
                                    IconButton({ modelView?.turnBy(60) }) {
                                        Icon(Icons.Outlined.RotateRight, "向右旋转")
                                    }
                                }
                            }
                            if (animations.isNotEmpty())
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    IconButton(
                                        { stepAnimation(-1) },
                                        enabled = animations.size > 1 && !loading && !exporting,
                                    ) {
                                        Icon(Icons.Outlined.ChevronLeft, "上一个动作")
                                    }
                                    OutlinedButton(
                                        { picker = "animation" },
                                        Modifier.weight(1f),
                                        enabled = !loading && !exporting,
                                    ) {
                                        Text("动作 · $selected", maxLines = 2)
                                    }
                                    IconButton(
                                        { stepAnimation(1) },
                                        enabled = animations.size > 1 && !loading && !exporting,
                                    ) {
                                        Icon(Icons.Outlined.ChevronRight, "下一个动作")
                                    }
                                }
                            if (skins.isNotEmpty())
                                OutlinedButton({ picker = "skin" }, Modifier.fillMaxWidth()) {
                                    Text("皮肤 · $skin", maxLines = 2)
                                }
                            if (hasMouth) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    OutlinedButton(
                                        { picker = "style" },
                                        Modifier.weight(1f),
                                        enabled = !loading && !exporting,
                                    ) {
                                        Text("风格 ${style+1}")
                                    }
                                    IconButton(
                                        { stepMouth(-1) },
                                        enabled = !loading && !exporting,
                                    ) {
                                        Icon(Icons.Outlined.ChevronLeft, "上一个嘴型")
                                    }
                                    OutlinedButton(
                                        { picker = "mouth" },
                                        Modifier.weight(1f),
                                        enabled = !loading && !exporting,
                                    ) {
                                        Text("嘴型 ${mouth+1}")
                                    }
                                    IconButton(
                                        { stepMouth(1) },
                                        enabled = !loading && !exporting,
                                    ) {
                                        Icon(Icons.Outlined.ChevronRight, "下一个嘴型")
                                    }
                                }
                            }
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(if (spine) "填充画面" else "自动旋转", Modifier.weight(1f))
                                Switch(
                                    if (spine) fill else rotate,
                                    { if (spine) fill = it else rotate = it },
                                )
                            }
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("省电预览 · 30 帧", Modifier.weight(1f))
                                Switch(lowPower, { lowPower = it })
                            }
                            OutlinedButton(
                                { bg = (bg + 1) % colors.size },
                                Modifier.fillMaxWidth(),
                            ) {
                                Text("背景 · ${listOf("浅色","深色","绿色","透明")[bg]}")
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("完整画面导出", Modifier.weight(1f))
                                Switch(fullExport, { fullExport = it }, enabled = !exporting)
                            }
                            Text(
                                "完整导出不受预览缩放和面板大小影响。PNG 保留透明背景；视频不含音轨，分辨率按设备编码能力适配。",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(720, 1080, 1920).forEach { edge ->
                                    FilterChip(
                                        exportEdge == edge,
                                        { exportEdge = edge },
                                        enabled = !exporting,
                                        label = { Text("$edge 像素") },
                                    )
                                }
                                listOf(5, 10).forEach { seconds ->
                                    FilterChip(
                                        exportSeconds == seconds,
                                        { exportSeconds = seconds },
                                        enabled = !exporting,
                                        label = { Text("$seconds 秒") },
                                    )
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    { png.launch("kivo-${id}.png") },
                                    Modifier.weight(1f),
                                    enabled = !loading && error == null && !exporting,
                                ) {
                                    Text("保存画面")
                                }
                                OutlinedButton(
                                    { mp4.launch("kivo-${id}.mp4") },
                                    Modifier.weight(1f),
                                    enabled = !loading && error == null && !exporting,
                                ) {
                                    Text("录制视频")
                                }
                            }
                            if (exporting) {
                                LinearProgressIndicator({ exportProgress }, Modifier.fillMaxWidth())
                                TextButton({ exportJob?.cancel() }) { Text("取消导出") }
                            }
                            message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            Text(
                                "单指移动／旋转，双指缩放。录像最长 10 秒，不含音轨；切到后台会取消录制。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (wide)
                        Row(Modifier.fillMaxSize()) {
                            canvas(Modifier.weight(1f).fillMaxHeight())
                            if (controls) panel(Modifier.width(300.dp).fillMaxHeight())
                        }
                    else
                        Column(Modifier.fillMaxSize()) {
                            canvas(Modifier.weight(1f).fillMaxWidth())
                            if (controls) {
                                // 固定可见的设置入口不随面板正文滚走，首次打开即可发现底部导出等选项。
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        "动作 · 画面 · 导出",
                                        Modifier.weight(1f),
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                    TextButton({
                                        scope.launch {
                                            panelScroll.animateScrollTo(
                                                if (panelScroll.canScrollForward)
                                                    panelScroll.maxValue
                                                else 0
                                            )
                                        }
                                    }) {
                                        Text(
                                            if (panelScroll.canScrollForward) "更多设置 ↓"
                                            else "返回设置顶部 ↑"
                                        )
                                    }
                                }
                                panel(Modifier.fillMaxWidth().heightIn(max = 280.dp))
                            }
                        }
                }
            }
        }
    }
    if (exporting)
        AlertDialog(
            onDismissRequest = {},
            title = { Text("正在导出完整画面") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LinearProgressIndicator({ exportProgress }, Modifier.fillMaxWidth())
                    Text("请保持应用在前台，完成后将写入所选位置。")
                }
            },
            confirmButton = { TextButton({ exportJob?.cancel() }) { Text("取消导出") } },
        )
    when (picker) {
        "animation" ->
            ChoiceDialog("选择动作", animations.map { it to it }, selected, { picker = null }) {
                val index = animations.indexOf(it)
                if (index >= 0) {
                    selected = it
                    spineView?.animation(it)
                    modelView?.animation(index)
                }
                picker = null
            }
        "skin" ->
            ChoiceDialog("选择皮肤", skins.map { it to it }, skin, { picker = null }) {
                skin = it
                spineView?.skin(it)
                picker = null
            }
        "style" ->
            ChoiceDialog(
                "嘴部贴图风格",
                (0..3).map { "$it" to "风格 ${it+1}" },
                "$style",
                { picker = null },
            ) {
                style = it.toInt()
                picker = null
            }
        "mouth" ->
            ChoiceDialog("嘴型", (0..63).map { "$it" to "嘴型 ${it+1}" }, "$mouth", { picker = null }) {
                mouth = it.toInt()
                picker = null
            }
    }
}
