package wiki.kivo.feature.organization

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import wiki.kivo.core.content.*
import wiki.kivo.core.designsystem.*
import wiki.kivo.core.model.*

@Composable
internal fun OrganizationMapCard(
    map: OrganizationMap,
    link: (String) -> Unit,
    image: (String) -> Unit,
) {
    var selected by rememberSaveable(map.name, map.image) { mutableIntStateOf(-1) }
    var full by rememberSaveable(map.name, map.image) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(map.name.ifBlank { "组织地图" }, style = MaterialTheme.typography.titleLarge)
        MapCanvas(map, selected, { selected = it })
        Text("点按地图编号或下方地标，阅读地点资料。", style = MaterialTheme.typography.bodySmall)
        OutlinedButton({ full = true }, enabled = map.image != null) { Text("放大探索地图") }
        map.landmarks.forEachIndexed { index, landmark ->
            ListItem(
                headlineContent = { Text(landmark.name.ifBlank { "地标 ${index + 1}" }) },
                leadingContent = {
                    Text("${index + 1}", color = MaterialTheme.colorScheme.primary)
                },
                trailingContent = { Icon(Icons.Outlined.ChevronRight, null) },
                modifier = Modifier.clickable { selected = index }.testTag("landmark_list_$index"),
            )
        }
    }
    if (full)
        Dialog({ full = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxSize()) {
                Column(Modifier.safeDrawingPadding().padding(12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            map.name,
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        IconButton({ full = false }) { Icon(Icons.Outlined.Close, "关闭地图") }
                    }
                    MapCanvas(
                        map,
                        selected,
                        { selected = it },
                        zoomable = true,
                        modifier = Modifier.weight(1f),
                    )
                    Text("双指缩放、拖动浏览；地图编号与下方列表一一对应。", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    map.landmarks.getOrNull(selected)?.let { landmark ->
        LandmarkDialog(landmark, link, image) { selected = -1 }
    }
}

/** 图像与标记共享同一坐标容器及缩放变换。使用图片真实宽高比，不能按裁剪后的卡片定位。 */
@Composable
private fun MapCanvas(
    map: OrganizationMap,
    selected: Int,
    select: (Int) -> Unit,
    zoomable: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var ratio by remember(map.image) { mutableFloatStateOf(1.8f) }
    var loaded by remember(map.image) { mutableStateOf(false) }
    var failed by remember(map.image) { mutableStateOf(false) }
    var attempt by remember(map.image) { mutableIntStateOf(0) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val settings = LocalKivoSettings.current
    val imageUrl = map.image
    val allowed =
        imageUrl != null &&
            settings.loadImages &&
            (settings.externalImages || UrlPolicy.isFirstPartyImage(imageUrl))
    Column(modifier) {
        if (zoomable)
            Row {
                TextButton({ zoom = (zoom * 1.5f).coerceAtMost(5f) }) { Text("放大") }
                TextButton({
                    zoom = 1f
                    pan = Offset.Zero
                }) {
                    Text("复位")
                }
            }
        Box(
            Modifier.then(if (zoomable) Modifier.weight(1f) else Modifier)
                .fillMaxWidth()
                .clipToBounds()
                .then(
                    if (zoomable)
                        Modifier.pointerInput(Unit) {
                            detectTransformGestures { _, delta, factor, _ ->
                                zoom = (zoom * factor).coerceIn(1f, 5f)
                                val limitX = size.width * (zoom - 1) / 2
                                val limitY = size.height * (zoom - 1) / 2
                                pan =
                                    Offset(
                                        (pan.x + delta.x).coerceIn(-limitX, limitX),
                                        (pan.y + delta.y).coerceIn(-limitY, limitY),
                                    )
                            }
                        }
                    else Modifier
                ),
            contentAlignment = Alignment.Center,
        ) {
            BoxWithConstraints(
                Modifier.fillMaxWidth().aspectRatio(ratio).graphicsLayer {
                    scaleX = zoom
                    scaleY = zoom
                    translationX = pan.x
                    translationY = pan.y
                }
            ) {
                if (allowed)
                    key(attempt) {
                        AsyncImage(
                            map.image,
                            map.name,
                            Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                            onSuccess = { result ->
                                val img = result.result.image
                                if (img.width > 0 && img.height > 0)
                                    ratio = img.width.toFloat() / img.height
                                loaded = true
                                failed = false
                            },
                            onError = {
                                loaded = false
                                failed = true
                            },
                        )
                    }
                if (!allowed) Text("图片已关闭或未收录，仍可通过地标列表阅读。", Modifier.padding(16.dp))
                else if (!loaded)
                    Text(if (failed) "地图图片加载失败" else "正在加载地图…", Modifier.padding(16.dp))
                val width = with(LocalDensity.current) { maxWidth.toPx() }
                val height = with(LocalDensity.current) { maxHeight.toPx() }
                val radius = with(LocalDensity.current) { 24.dp.toPx() }
                if (loaded && allowed)
                    map.landmarks.forEachIndexed { index, mark ->
                        val x = mark.x
                        val y = mark.y
                        if (x != null && y != null) {
                            Surface(
                                onClick = { select(index) },
                                shape = CircleShape,
                                color = Color.Transparent,
                                modifier =
                                    Modifier.offset {
                                            IntOffset(
                                                (x * width - radius).roundToInt(),
                                                (y * height - radius).roundToInt(),
                                            )
                                        }
                                        .size(48.dp)
                                        .semantics {
                                            contentDescription = "地标 ${index + 1}：${mark.name}"
                                        }
                                        .testTag("map_mark_$index"),
                            ) {
                                // 视觉标记保持轻量，触控范围仍为 48dp，避免遮住校园建筑。
                                Box(contentAlignment = Alignment.Center) {
                                    Surface(
                                        Modifier.size(28.dp),
                                        shape = CircleShape,
                                        color =
                                            if (selected == index)
                                                MaterialTheme.colorScheme.tertiary
                                            else MaterialTheme.colorScheme.primary,
                                        border = BorderStroke(1.5.dp, Color.White),
                                        shadowElevation = 2.dp,
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                "${index + 1}",
                                                style = MaterialTheme.typography.labelMedium,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
            }
        }
        if (failed)
            TextButton({
                attempt++
                failed = false
            }) {
                Text("重试地图图片")
            }
    }
}

@Composable
private fun LandmarkDialog(
    landmark: Landmark,
    link: (String) -> Unit,
    image: (String) -> Unit,
    close: () -> Unit,
) {
    val blocks by
        produceState<List<ContentBlock>>(emptyList(), landmark.description) {
            value = withContext(Dispatchers.Default) { ContentParser.parse(landmark.description) }
        }
    Dialog(close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            Modifier.padding(20.dp).widthIn(max = 720.dp).fillMaxWidth().fillMaxHeight(.75f),
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        landmark.name,
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    IconButton(close) { Icon(Icons.Outlined.Close, "关闭地标资料") }
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (landmark.description.isBlank()) item { Text("该地标暂无详细资料") }
                    itemsIndexed(blocks) { _, block -> ContentBlockView(block, link, image) }
                }
            }
        }
    }
}
