package wiki.kivo.app.screens

import android.content.Intent
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import wiki.kivo.app.AppViewModel
import wiki.kivo.core.content.*
import wiki.kivo.core.data.ContentRepository
import wiki.kivo.core.data.local.LibraryRepository
import wiki.kivo.core.data.local.SettingsRepository
import wiki.kivo.core.data.network.friendlyMessage
import wiki.kivo.core.designsystem.*
import wiki.kivo.core.model.*

data class ReaderState(
    val detail: LoadState<ContentDetail> = LoadState(),
    val blocks: List<ContentBlock> = emptyList(),
    val position: Pair<Int, Int>? = null,
)

class ReaderViewModel(
    private val repository: ContentRepository,
    private val library: LibraryRepository,
    private val preferences: SettingsRepository,
    private val account: wiki.kivo.core.data.account.AccountRepository,
) : ViewModel() {
    val state = MutableStateFlow(ReaderState())
    private var job: Job? = null
    private var loadedKey: EntityKey? = null

    init {
        viewModelScope.launch {
            account.state
                .map { it.user?.id }
                .distinctUntilChanged()
                .collect { loadedKey?.let { key -> load(key, true) } }
        }
    }

    fun load(key: EntityKey, force: Boolean = false) {
        if (!force && loadedKey == key) return
        loadedKey = key
        job?.cancel()
        state.value = ReaderState()
        job = viewModelScope.launch {
            val progress = library.progress(key)
            repository.detail(key, force).collectLatest { publicDetail ->
                val detail =
                    if (
                        publicDetail.error != null &&
                            publicDetail.value == null &&
                            !publicDetail.loading &&
                            key.type == EntityType.ARTICLE &&
                            account.state.value.user != null
                    ) {
                        try {
                            LoadState(account.readArticle(key.id), loading = false)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: Exception) {
                            LoadState<ContentDetail>(
                                loading = false,
                                error = failure.friendlyMessage(),
                            )
                        }
                    } else publicDetail
                val blocks =
                    detail.value
                        ?.body
                        ?.let { withContext(Dispatchers.Default) { ContentParser.parse(it) } }
                        .orEmpty()
                state.value = ReaderState(detail, blocks, progress)
            }
        }
    }

    fun bookmark(saved: Boolean) {
        if (state.value.detail.value?.privateContent == true) return
        state.value.detail.value?.card?.let { card ->
            viewModelScope.launch {
                if (saved) library.removeBookmark(card.key) else library.addBookmark(card)
            }
        }
    }

    suspend fun progress(index: Int, offset: Int) {
        if (state.value.detail.value?.privateContent == true) return
        val card = state.value.detail.value?.card ?: return
        if (preferences.settings.first().rememberHistory) library.record(card, index, offset)
    }
}

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun ReaderRoute(
    key: EntityKey,
    app: AppViewModel,
    bookmarked: Boolean,
    onCard: (ContentCard) -> Unit,
    onWebsite: (String) -> Unit,
    onSettings: () -> Unit,
    onLogin: () -> Unit = {},
    onSensitiveContent: (Boolean) -> Unit = {},
) {
    val vm: ReaderViewModel =
        viewModel(
            factory =
                viewModelFactory {
                    initializer {
                        ReaderViewModel(app.repository, app.library, app.preferences, app.account)
                    }
                }
        )
    val state by vm.state.collectAsStateWithLifecycle()
    val sensitiveCallback by rememberUpdatedState(onSensitiveContent)
    DisposableEffect(state.detail.value?.privateContent == true) {
        sensitiveCallback(state.detail.value?.privateContent == true)
        onDispose { sensitiveCallback(false) }
    }
    LaunchedEffect(key) { vm.load(key) }
    val list = rememberLazyListState()
    var restored by rememberSaveable { mutableStateOf(false) }
    var toc by rememberSaveable { mutableStateOf(false) }
    var imageUrl by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val reduce = LocalKivoSettings.current.reducedMotion
    val currentCard by
        rememberUpdatedState(state.detail.value?.takeUnless { it.privateContent }?.card)
    DisposableEffect(key, list) {
        onDispose {
            currentCard?.let {
                app.recordProgress(
                    it,
                    list.firstVisibleItemIndex,
                    list.firstVisibleItemScrollOffset,
                )
            }
        }
    }
    LaunchedEffect(state.position, state.blocks) {
        if (!restored && state.detail.value != null) {
            val p = state.position ?: (0 to 0)
            list.scrollToItem(p.first.coerceIn(0, state.blocks.size + 2), p.second)
            restored = true
        }
    }
    LaunchedEffect(list, state.detail.value?.card?.key) {
        if (state.detail.value != null) {
            // 停止滚动后再写数据库，避免逐帧磁盘写入；离开页面时由应用级作用域补记最终位置。
            snapshotFlow { list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset }
                .distinctUntilChanged()
                .debounce(500)
                .collect { (index, offset) -> vm.progress(index, offset) }
        }
    }
    Column(Modifier.fillMaxSize().testTag("reader_screen")) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            IconButton(
                onClick = { vm.bookmark(bookmarked) },
                enabled = state.detail.value != null && state.detail.value?.privateContent != true,
                modifier = Modifier.testTag("bookmark"),
            ) {
                Icon(
                    if (bookmarked) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                    if (bookmarked) "取消收藏" else "收藏",
                )
            }
            IconButton(
                onClick = { toc = true },
                enabled = state.blocks.any { it is ContentBlock.Text && it.heading > 0 },
            ) {
                Icon(Icons.Outlined.FormatListBulleted, "文章目录")
            }
            IconButton(onClick = onSettings) { Icon(Icons.Outlined.TextFields, "阅读设置") }
            IconButton(onClick = { vm.load(key, true) }) { Icon(Icons.Outlined.Refresh, "刷新资料") }
            if (key.type.webPath.isNotEmpty() && state.detail.value?.privateContent != true)
                IconButton(
                    onClick = {
                        context.startActivity(
                            Intent.createChooser(
                                Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(
                                        Intent.EXTRA_TEXT,
                                        "${state.detail.value?.card?.title.orEmpty()}\n${key.webUrl}",
                                    )
                                },
                                "分享资料",
                            )
                        )
                    }
                ) {
                    Icon(Icons.Outlined.Share, "分享")
                }
        }
        SelectionContainer {
            LazyColumn(
                state = list,
                modifier = Modifier.fillMaxSize().testTag("reader_body"),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item("title") {
                    Column(
                        Modifier.widthIn(max = 720.dp).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            "KIVO ARCHIVE  /  ${key.type.label}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            state.detail.value
                                ?.student
                                ?.translated(LocalKivoSettings.current.translation)
                                ?.displayName ?: state.detail.value?.card?.title ?: "正在打开资料…",
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        state.detail.value?.updatedAt?.let {
                            Text(
                                "最近编辑于 ${shortDate(it, pattern = "yyyy.MM.dd HH:mm")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        SectionStatus(state.detail) { vm.load(key, true) }
                        if (
                            key.type == EntityType.ARTICLE &&
                                state.detail.error != null &&
                                state.detail.value == null
                        )
                            TextButton(onClick = onLogin) { Text("登录有权限的账号") }
                        if (state.detail.value?.privateContent == true)
                            StatusNote("权限内容 · 仅在本次登录期间阅读，不保存正文、收藏或足迹")
                    }
                }
                item("cover") {
                    state.detail.value?.card?.image?.let { url ->
                        KivoImage(
                            url,
                            state.detail.value?.card?.title,
                            Modifier.widthIn(max = 720.dp).fillMaxWidth().height(210.dp).clickable {
                                imageUrl = url
                            },
                            ContentScale.Fit,
                        )
                    }
                }
                item("scope") {
                    if (
                        key.type in
                            listOf(EntityType.STUDENT, EntityType.ITEM, EntityType.EQUIPMENT)
                    )
                        Box(Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
                            StatusNote("这里是首页资料速览。完整资料与鉴赏功能将随后接入，可先在网站查看。", "网站") {
                                onWebsite(key.webUrl)
                            }
                        }
                }
                item("skills") {
                    val skills = state.detail.value?.skills.orEmpty()
                    if (skills.isNotEmpty())
                        Column(
                            Modifier.widthIn(max = 720.dp).fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            SectionTitle("技能速览")
                            skills.forEach { skill -> key(skill.title) { SkillPreviewCard(skill) } }
                        }
                }
                itemsIndexed(state.blocks, key = { index, _ -> "block_$index" }) { _, block ->
                    Box(Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
                        ContentBlockView(
                            block,
                            onLink = { url ->
                                val entity = UrlPolicy.entity(url)
                                if (entity != null) onCard(ContentCard(entity, ""))
                                else onWebsite(url)
                            },
                            onImage = { imageUrl = it },
                        )
                    }
                }
                item("source") {
                    val detail = state.detail.value
                    Column(
                        Modifier.widthIn(max = 720.dp).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (detail != null && detail.body.isBlank())
                            Text(
                                "这条资料暂未提供正文。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        detail?.card?.sourceUrl?.let { url ->
                            OutlinedButton(
                                onClick = {
                                    val entity = UrlPolicy.entity(url)
                                    if (entity != null) onCard(ContentCard(entity, ""))
                                    else onWebsite(url)
                                }
                            ) {
                                Text("查看来源内容")
                                Icon(
                                    Icons.Outlined.OpenInNew,
                                    null,
                                    Modifier.padding(start = 8.dp).size(18.dp),
                                )
                            }
                        }
                        Text(
                            "资料来自基沃托斯古书馆 · kivo.wiki",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
    if (toc)
        ModalBottomSheet(onDismissRequest = { toc = false }) {
            LazyColumn(contentPadding = PaddingValues(24.dp)) {
                item { Text("文章目录", style = MaterialTheme.typography.titleLarge) }
                state.blocks.forEachIndexed { index, block ->
                    if (block is ContentBlock.Text && block.heading > 0)
                        item {
                            TextButton(
                                onClick = {
                                    toc = false
                                    scope.launch {
                                        if (reduce) list.scrollToItem(index + 4)
                                        else list.animateScrollToItem(index + 3)
                                    }
                                }
                            ) {
                                Text(block.spans.joinToString("") { it.text })
                            }
                        }
                }
            }
        }
    imageUrl?.let { ImageViewer(it) { imageUrl = null } }
}

/** 本期提供正文单图缩放；大图库的分块解码、队列与下载留给媒体模块。 */
@Composable
private fun ImageViewer(url: String, close: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Dialog(
        onDismissRequest = close,
        properties =
            DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Column(Modifier.fillMaxSize().background(Color(0xFF101922)).safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(
                    onClick = {
                        scale = 1f
                        offset = Offset.Zero
                    }
                ) {
                    Text("重置缩放", color = Color.White)
                }
                IconButton(onClick = close) {
                    Icon(Icons.Outlined.Close, "关闭图片", tint = Color.White)
                }
            }
            Box(
                Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(0.dp)).pointerInput(
                    Unit
                ) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 4f)
                        offset =
                            if (scale == 1f) Offset.Zero
                            else
                                (offset + pan).let {
                                    Offset(
                                        it.x.coerceIn(
                                            -size.width * (scale - 1) / 2,
                                            size.width * (scale - 1) / 2,
                                        ),
                                        it.y.coerceIn(
                                            -size.height * (scale - 1) / 2,
                                            size.height * (scale - 1) / 2,
                                        ),
                                    )
                                }
                    }
                }
            ) {
                KivoImage(
                    url,
                    "完整正文图片",
                    Modifier.fillMaxSize().graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
                    ContentScale.Fit,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                IconButton(
                    onClick = {
                        scale = (scale - .5f).coerceAtLeast(1f)
                        if (scale == 1f) offset = Offset.Zero
                    }
                ) {
                    Icon(Icons.Outlined.Remove, "缩小", tint = Color.White)
                }
                Text("${(scale * 100).toInt()}%", Modifier.padding(14.dp), color = Color.White)
                IconButton(onClick = { scale = (scale + .5f).coerceAtMost(4f) }) {
                    Icon(Icons.Outlined.Add, "放大", tint = Color.White)
                }
            }
        }
    }
}

/** 只使用接口给出的逐级描述，不自行推算数值。默认等级在偏好变化时重新应用。 */
@Composable
private fun SkillPreviewCard(skill: SkillPreview) {
    val settings = LocalKivoSettings.current
    var level by
        rememberSaveable(skill.title, settings.levelMax) {
            mutableIntStateOf(if (settings.levelMax) skill.levels.lastIndex else 0)
        }
    var blocks by remember { mutableStateOf<List<ContentBlock>>(emptyList()) }
    LaunchedEffect(skill, level) {
        blocks =
            withContext(Dispatchers.Default) {
                ContentParser.parse(skill.levels.getOrElse(level) { "" })
            }
    }
    KivoCard {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                if (settings.translation == TranslationMode.CN)
                    skill.titleCn.ifBlank { skill.title }
                else skill.title,
                style = MaterialTheme.typography.titleMedium,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                skill.levels.indices.forEach { index ->
                    FilterChip(
                        selected = level == index,
                        onClick = { level = index },
                        label = { Text("Lv." + (index + 1)) },
                    )
                }
            }
            blocks.forEach { ContentBlockView(it, {}, {}) }
        }
    }
}
