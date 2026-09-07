package wiki.kivo.feature.character

import android.content.Intent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import wiki.kivo.core.content.*
import wiki.kivo.core.designsystem.*
import wiki.kivo.core.model.*
import wiki.kivo.feature.organization.RelationGroup

internal data class DetailPart(
    val key: String,
    val title: String? = null,
    val renderTitle: Boolean = true,
    val content: @Composable () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CharacterDetailScreen(
    id: Int,
    bookmarked: Boolean,
    open: (ContentCard) -> Unit,
    website: (String) -> Unit,
    login: () -> Unit,
    costume: (Int, Int) -> Unit,
    vm: CharacterViewModel,
    initialTab: Int = 0,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val blocks by vm.blocks.collectAsStateWithLifecycle()
    val settings = LocalKivoSettings.current
    var cn by rememberSaveable(id) { mutableStateOf(settings.translation == TranslationMode.CN) }
    val translation = if (cn) TranslationMode.CN else TranslationMode.FAN
    var tab by rememberSaveable(id) { mutableIntStateOf(initialTab.coerceIn(0, 3)) }
    var mode by rememberSaveable(id) { mutableIntStateOf(0) }
    var showInfo by rememberSaveable(id) { mutableStateOf(false) }
    var toc by rememberSaveable(id) { mutableStateOf(false) }
    var photo by rememberSaveable(id) { mutableStateOf<String?>(null) }
    var video by rememberSaveable(id) { mutableStateOf<String?>(null) }
    var mediaId by rememberSaveable(id) { mutableIntStateOf(0) }
    var mediaSpine by rememberSaveable(id) { mutableStateOf(true) }
    var stickyHeight by remember { mutableIntStateOf(96) }
    val tabPositions = remember { List(4) { mutableFloatStateOf(100000f) } }
    var pagerTop by remember { mutableFloatStateOf(0f) }
    val pager = rememberPagerState(initialPage = tab) { 4 }
    // 每个分类拥有独立且可恢复的纵向位置。Pager 本身处理拖动、半页停留和速度结算。
    val lists = List(4) { rememberLazyListState() }
    val list = lists[pager.currentPage]
    val scope = rememberCoroutineScope()
    fun alignHeader(target: Int) {
        val current = lists[pager.settledPage]
        if (target != pager.settledPage && lists[target].firstVisibleItemIndex < 2) {
            // 远端页尚未测量时 scrollToItem 会等待布局，反过来阻塞 Pager 开始切换。
            // requestScrollToItem 只登记首次测量锚点，允许直接从“数据”切到“鉴赏/语音”。
            lists[target].requestScrollToItem(
                current.firstVisibleItemIndex.coerceAtMost(1),
                if (current.firstVisibleItemIndex == 0) current.firstVisibleItemScrollOffset else 0,
            )
        }
    }
    fun switchTab(target: Int) {
        if (target !in 0..3) return
        scope.launch {
            alignHeader(target)
            if (settings.reducedMotion) pager.scrollToPage(target)
            else pager.animateScrollToPage(target)
        }
    }
    LaunchedEffect(pager.targetPage) { alignHeader(pager.targetPage) }
    LaunchedEffect(pager.settledPage) { tab = pager.settledPage }
    val context = LocalContext.current
    LaunchedEffect(id) { vm.load(id) }
    fun link(url: String) {
        val entity = UrlPolicy.entity(url)
        if (entity != null) open(ContentCard(entity, "")) else website(url)
    }
    val profile = state.value
    if (profile == null) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionStatus(state) { vm.load(id, true) }
            if (state.loading) CircularProgressIndicator() else Text("暂时无法读取这份角色档案")
        }
        return
    }
    val battle = profile.battles.getOrNull(mode) ?: profile.battles.firstOrNull()
    val headerReactions = rememberReactionController(profile.infoDeclare, vm)
    val partsByPage =
        (0..3).map { page ->
            when (page) {
                0 ->
                    battleParts(
                        profile,
                        battle,
                        translation,
                        vm,
                        open,
                        ::link,
                        { photo = it },
                        { video = it },
                        login,
                    )
                1 -> infoParts(profile, blocks, vm, ::link, { photo = it }, login)
                2 ->
                    appreciationParts(profile, vm.repository, ::link, { photo = it }) { value, spine
                        ->
                        mediaId = value
                        mediaSpine = spine
                    }
                else -> voiceParts(profile, vm, pager.settledPage == 3 && !pager.isScrollInProgress)
            }
        }
    val parts = partsByPage[pager.currentPage]
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        if (settings.showCharacterBackground && settings.loadImages && profile.lobbyImage != null) {
            // 大厅静态图在网站中是页面氛围背景，而非一张待逐条浏览的图集。
            // 固定在视口后方并叠加主题遮罩，保证长文滚动时文字对比度稳定。
            KivoImage(
                profile.lobbyImage,
                null,
                Modifier.matchParentSize().alpha(.24f),
                ContentScale.Crop,
                Alignment.TopCenter,
            )
            Box(
                Modifier.matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.background.copy(alpha = .48f),
                                MaterialTheme.colorScheme.background.copy(alpha = .78f),
                                MaterialTheme.colorScheme.background.copy(alpha = .94f),
                            )
                        )
                    )
            )
        }
        val categoryHeader: @Composable () -> Unit = {
            Surface(
                modifier = Modifier.onSizeChanged { stickyHeight = it.height },
                color = MaterialTheme.colorScheme.background,
            ) {
                Column {
                    ScrollableTabRow(
                        pager.currentPage,
                        edgePadding = 8.dp,
                        containerColor = MaterialTheme.colorScheme.background,
                        indicator = { positions ->
                            // 指示线与手指使用同一个分页偏移；按住半页时也停在两分类之间。
                            val page = pager.currentPage
                            val fraction = pager.currentPageOffsetFraction
                            val next =
                                (page + if (fraction < 0) -1 else 1).coerceIn(
                                    0,
                                    positions.lastIndex,
                                )
                            val progress = kotlin.math.abs(fraction)
                            val left =
                                positions[page].left +
                                    (positions[next].left - positions[page].left) * progress
                            val width =
                                positions[page].width +
                                    (positions[next].width - positions[page].width) * progress
                            TabRowDefaults.SecondaryIndicator(
                                Modifier.fillMaxWidth()
                                    .wrapContentSize(Alignment.BottomStart)
                                    .offset(x = left)
                                    .width(width)
                            )
                        },
                    ) {
                        listOf("数据", "资料", "鉴赏", "语音").forEachIndexed { index, title ->
                            Tab(
                                pager.currentPage == index,
                                { switchTab(index) },
                                text = { Text(title) },
                                modifier = Modifier.testTag("character_tab_$index"),
                            )
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            profile.student.translated(translation).displayName,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton({ toc = true }) {
                            Icon(Icons.Outlined.FormatListBulleted, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("本页目录")
                        }
                    }
                }
            }
        }
        HorizontalPager(
            state = pager,
            modifier =
                Modifier.fillMaxSize().testTag("character_pager").onGloballyPositioned {
                    pagerTop = it.positionInWindow().y
                },
            userScrollEnabled = settings.swipeCategories,
            beyondViewportPageCount = 1,
        ) { page ->
            val parts = partsByPage[page]
            LazyColumn(
                state = lists[page],
                modifier =
                    Modifier.widthIn(max = 1000.dp)
                        .fillMaxSize()
                        .testTag(
                            if (page == pager.currentPage) "character_detail"
                            else "character_detail_$page"
                        ),
                contentPadding = PaddingValues(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item("identity") {
                    Column(
                        Modifier.padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SectionStatus(state) { vm.load(id, true) }
                        KivoCard {
                            Column(
                                Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    CharacterPortrait(
                                        profile,
                                        id,
                                        translation,
                                        { photo = it },
                                    )
                                    Column(
                                        Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Text(
                                            "CHARACTER · ${id.toString().padStart(3,'0')}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                        Text(
                                            profile.fullName.display(translation),
                                            style = MaterialTheme.typography.headlineSmall,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        val skin =
                                            WikiText(profile.student.skin, profile.student.skinCn)
                                                .display(translation)
                                        if (skin.isNotBlank())
                                            Text(
                                                skin,
                                                style = MaterialTheme.typography.titleMedium,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                        Text(
                                            if (profile.isNpc) "NPC 档案"
                                            else
                                                profile.info.find { it.label == "年级" }?.value
                                                    ?: "角色档案",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        TextButton(
                                            { showInfo = true },
                                            contentPadding = PaddingValues(0.dp),
                                        ) {
                                            Text("更多信息")
                                            Icon(
                                                Icons.Outlined.ExpandMore,
                                                null,
                                                Modifier.size(18.dp),
                                            )
                                        }
                                    }
                                }
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        if (cn) "国服翻译" else "民间翻译",
                                        Modifier.weight(1f),
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                    Switch(
                                        cn,
                                        { cn = it },
                                        Modifier.testTag("character_translation"),
                                    )
                                    IconButton({ vm.bookmark(bookmarked) }) {
                                        Icon(
                                            if (bookmarked) Icons.Outlined.Bookmark
                                            else Icons.Outlined.BookmarkBorder,
                                            if (bookmarked) "取消本地收藏" else "收藏角色",
                                        )
                                    }
                                    IconButton({
                                        context.startActivity(
                                            Intent.createChooser(
                                                Intent(Intent.ACTION_SEND).apply {
                                                    type = "text/plain"
                                                    putExtra(
                                                        Intent.EXTRA_TEXT,
                                                        "${profile.fullName.display(translation)}\n${profile.student.key.webUrl}",
                                                    )
                                                },
                                                "分享角色",
                                            )
                                        )
                                    }) {
                                        Icon(Icons.Outlined.Share, "分享角色链接")
                                    }
                                }
                                if (profile.introduction.display(translation).isNotBlank())
                                    ExpandableIntroduction(
                                        profile.introduction.display(translation),
                                        ::link,
                                    ) {
                                        photo = it
                                    }
                                if (profile.signature.isNotBlank())
                                    Text(
                                        "—— ${profile.signature}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontStyle = FontStyle.Italic,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                ProfileHighlights(profile, battle, translation, vm, open)
                            }
                        }
                        if (profile.costumes.size > 1)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(profile.costumes, key = { it.id }) { skin ->
                                    FilterChip(
                                        skin.id == id,
                                        { if (skin.id != id) costume(skin.id, tab) },
                                        label = {
                                            Text(skin.title.display(translation).ifBlank { "原皮" })
                                        },
                                        leadingIcon = {
                                            KivoImage(
                                                skin.avatar,
                                                null,
                                                Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)),
                                            )
                                        },
                                        modifier =
                                            Modifier.heightIn(min = 48.dp)
                                                .testTag("costume_${skin.id}"),
                                    )
                                }
                            }
                        if (profile.battles.size > 1)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(profile.battles.indices.toList()) { index ->
                                    FilterChip(
                                        mode == index,
                                        { mode = index },
                                        label = {
                                            Text(
                                                profile.battles[index].style.ifBlank {
                                                    "类型 ${index+1}"
                                                }
                                            )
                                        },
                                        modifier =
                                            Modifier.heightIn(min = 48.dp)
                                                .testTag("combat_mode_$index"),
                                    )
                                }
                            }
                        ReactionRow(profile.infoDeclare, vm, login, headerReactions)
                    }
                }
                stickyHeader("tabs") {
                    // 页内只保留吸顶占位；真实分类栏在 Pager 上层绘制，不随左右拖动重复出现。
                    Box(
                        Modifier.fillMaxWidth()
                            .height(with(LocalDensity.current) { stickyHeight.toDp() })
                            .onGloballyPositioned {
                                tabPositions[page].floatValue =
                                    (it.positionInWindow().y - pagerTop).coerceAtLeast(0f)
                            }
                    )
                }
                items(
                    parts,
                    key = { it.key },
                    contentType = { if (it.title == null) "body" else "section" },
                ) { part ->
                    Column(
                        Modifier.padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        part.title
                            ?.takeIf { part.renderTitle }
                            ?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.semantics { heading() },
                                )
                            }
                        part.content()
                    }
                }
                item("related-heading") {
                    Text(
                        "与之相关的角色",
                        Modifier.padding(horizontal = 16.dp).semantics { heading() },
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
                items(
                    (listOf(profile.mainRelation) + profile.relations).filter { it > 0 }.distinct(),
                    key = { "related-$it" },
                ) { relationId ->
                    Box(Modifier.padding(horizontal = 16.dp)) {
                        RelationGroup(
                            relationId,
                            vm.organizations,
                            open,
                            if (relationId == profile.mainRelation) "主要关系" else "次要关系",
                            id,
                        )
                    }
                }
                item("updated") {
                    Text(
                        "最近编辑 · ${shortDate(profile.updatedAt,pattern="yyyy.MM.dd HH:mm")}",
                        Modifier.padding(horizontal = 20.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Box(
            Modifier.widthIn(max = 1000.dp).fillMaxWidth().graphicsLayer {
                translationY = tabPositions[pager.currentPage].floatValue
            }
        ) {
            categoryHeader()
        }
    }
    if (showInfo)
        ModalBottomSheet(
            { showInfo = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            LazyColumn(
                Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item { Text("更多基本信息", style = MaterialTheme.typography.headlineSmall) }
                item {
                    val highlighted = setOf("别名", "身高", "生日", "日语配音", "中文配音")
                    FieldRows(profile.info.filterNot { it.label in highlighted })
                }
                if (battle != null)
                    item {
                        FieldRows(
                            listOf(
                                InfoField(
                                    "游戏角色 ID",
                                    battle.characterId.takeIf { it > 0 }?.toString() ?: "未公开",
                                ),
                                InfoField("开发代号", battle.devName.ifBlank { "未公开" }),
                                InfoField("古书馆编号", id.toString()),
                            )
                        )
                    }
                item { Text("所属组织与全部关系", style = MaterialTheme.typography.titleMedium) }
                item { ReferenceRow(profile.school, false, translation, vm.repository, open) }
                items(
                    (listOf(profile.mainRelation) + profile.relations).filter { it > 0 }.distinct()
                ) {
                    ReferenceRow(it, true, translation, vm.repository, open)
                }
                item { TextButton({ showInfo = false }, Modifier.fillMaxWidth()) { Text("关闭") } }
            }
        }
    if (toc)
        ChoiceDialog(
            "本页目录",
            parts.mapIndexedNotNull { index, p -> p.title?.let { index.toString() to it } } +
                (parts.size.toString() to "与之相关的角色"),
            "",
            { toc = false },
        ) { index ->
            toc = false
            scope.launch { list.animateScrollToItem(index.toInt() + 2, -stickyHeight) }
        }
    photo?.let {
        CharacterImageViewer(
            it,
            (profile.gallery.flatMap { g -> g.images } +
                    listOfNotNull(profile.sdImage, profile.lobbyImage, profile.student.avatar))
                .distinct(),
            vm.assets,
            { photo = null },
        )
    }
    video?.let { CharacterVideoDialog(it, vm.playback, { video = null }) }
    if (mediaId > 0) CharacterMediaDialog(mediaId, mediaSpine, vm, { mediaId = 0 })
}

/** 官网会在角色简介旁放置游戏内小人。App 在同一紧凑位置中保留小人与头像， 不为两张图另外拉长鉴赏页。减少动效时立即切换，其余情况用半程换面的 Y 轴翻转。 */
@Composable
private fun CharacterPortrait(
    profile: CharacterProfile,
    id: Int,
    translation: TranslationMode,
    openImage: (String) -> Unit,
) {
    val settings = LocalKivoSettings.current
    val density = LocalDensity.current.density
    val hasSd = !profile.sdImage.isNullOrBlank()
    var avatarSide by rememberSaveable(id) { mutableStateOf(false) }
    val turn by
        animateFloatAsState(
            targetValue = if (avatarSide) 180f else 0f,
            animationSpec = tween(if (settings.reducedMotion) 0 else 420),
            label = "character portrait flip",
        )
    val showSd = hasSd && turn < 90f
    val url = if (showSd) requireNotNull(profile.sdImage) else profile.student.avatar
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        KivoImage(
            url,
            if (showSd) "${profile.fullName.display(translation)}的游戏内形象"
            else "${profile.fullName.display(translation)}的角色头像",
            Modifier.width(108.dp)
                .height(140.dp)
                .graphicsLayer {
                    rotationY = if (turn <= 90f) turn else turn - 180f
                    cameraDistance = 12f * density
                }
                .clip(RoundedCornerShape(16.dp))
                .clickable(enabled = url != null) { url?.let(openImage) },
            if (showSd) ContentScale.Fit else ContentScale.Crop,
        )
        if (hasSd)
            IconButton(
                onClick = { avatarSide = !avatarSide },
                modifier = Modifier.size(40.dp).testTag("character_portrait_flip"),
            ) {
                Icon(
                    Icons.Outlined.SwapHoriz,
                    if (avatarSide) "切换为游戏内形象" else "切换为角色头像",
                    Modifier.size(20.dp),
                )
            }
    }
}

/** 将官网首屏的高频档案字段直接呈现；窄屏或大字号自动回落为单列。 */
@Composable
private fun ProfileHighlights(
    profile: CharacterProfile,
    battle: BattleProfile?,
    translation: TranslationMode,
    vm: CharacterViewModel,
    website: (ContentCard) -> Unit,
) {
    fun value(label: String) =
        profile.info.firstOrNull { it.label == label }?.value?.ifBlank { null } ?: "未公开"
    val fields =
        listOf(
            InfoField(
                "稀有度",
                battle?.rarity?.takeIf { it > 0 }?.coerceAtMost(5)?.let { "★".repeat(it) } ?: "未公开",
            ),
            InfoField("身高", value("身高")),
            InfoField("生日", value("生日")),
            InfoField("日语配音", value("日语配音")),
            InfoField("中文配音", value("中文配音")),
            InfoField("别名", value("别名")),
        )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("角色信息", style = MaterialTheme.typography.titleSmall)
        BoxWithConstraints {
            // 判断实际卡片内宽度而非整机宽度；字号放大时留出文字宽度，避免挤压。
            val single = maxWidth / LocalDensity.current.fontScale < 260.dp
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(
                    maxItemsInEachRow = if (single) 1 else 2,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HighlightReference(
                        "所属组织",
                        profile.school,
                        false,
                        translation,
                        vm,
                        website,
                        Modifier.weight(1f),
                    )
                    HighlightReference(
                        "主要关系",
                        profile.mainRelation,
                        true,
                        translation,
                        vm,
                        website,
                        Modifier.weight(1f),
                    )
                }
                fields.take(3).chunked(if (single) 1 else 3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { HighlightField(it, Modifier.weight(1f)) }
                    }
                }
                fields.drop(3).take(2).chunked(if (single) 1 else 2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { HighlightField(it, Modifier.weight(1f)) }
                    }
                }
                HighlightField(fields.last(), Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun HighlightField(field: InfoField, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.heightIn(min = 64.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .78f),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
            Text(
                field.label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(field.value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun HighlightReference(
    label: String,
    id: Int,
    relation: Boolean,
    translation: TranslationMode,
    vm: CharacterViewModel,
    website: (ContentCard) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (id <= 0) {
        HighlightField(InfoField(label, "未收录"), modifier)
        return
    }
    Surface(
        modifier = modifier.heightIn(min = 64.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .78f),
    ) {
        ReferenceRow(
            id,
            relation,
            translation,
            vm.repository,
            website,
            label,
            Modifier.padding(horizontal = 8.dp),
        )
    }
}

@Composable
internal fun WikiBody(source: String, link: (String) -> Unit, image: (String) -> Unit) {
    val blocks by
        produceState<List<ContentBlock>?>(null, source) {
            value = withContext(Dispatchers.Default) { ContentParser.parse(source) }
        }
    SelectionContainer {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            blocks?.forEach { ContentBlockView(it, link, image) }
        }
    }
}

@Composable
private fun ExpandableIntroduction(
    source: String,
    link: (String) -> Unit,
    image: (String) -> Unit,
) {
    var expanded by rememberSaveable(source) { mutableStateOf(false) }
    Column {
        if (expanded) WikiBody(source, link, image)
        else
            Text(
                source
                    .replace(Regex("<[^>]+>"), " ")
                    .replace(Regex("[>#]"), "")
                    .replace(Regex("\\s+"), " ")
                    .trim(),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        TextButton({ expanded = !expanded }, contentPadding = PaddingValues(0.dp)) {
            Text(if (expanded) "收起简介" else "阅读完整简介")
        }
    }
}
