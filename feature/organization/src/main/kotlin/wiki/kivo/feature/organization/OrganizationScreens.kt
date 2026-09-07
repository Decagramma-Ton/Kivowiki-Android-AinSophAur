package wiki.kivo.feature.organization

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import wiki.kivo.core.content.*
import wiki.kivo.core.designsystem.*
import wiki.kivo.core.model.*

@Composable
fun OrganizationCatalogScreen(vm: OrganizationViewModel, open: (ContentCard) -> Unit) {
    val state by vm.catalog.collectAsStateWithLifecycle()
    val settings = LocalKivoSettings.current
    val translation = settings.translation
    val compact = settings.compactOrganization
    // 常规手机三列；大字体主动减少列数，组织名称保留完整换行，不靠截断节省空间。
    val fontScale = LocalDensity.current.fontScale
    val cardWidth =
        when {
            fontScale >= 1.7f -> 180.dp
            fontScale > 1.3f -> 132.dp
            else -> 96.dp
        }
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(vm) { vm.loadCatalog() }
    val entries =
        state.value.orEmpty().filter {
            query.isBlank() ||
                it.name.original.contains(query.trim(), true) ||
                it.name.cn.contains(query.trim(), true)
        }
    LazyVerticalGrid(
        if (compact) GridCells.Fixed(1) else GridCells.Adaptive(cardWidth),
        Modifier.fillMaxSize().testTag("organization_catalog"),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "ORGANIZATION NOTES",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "今天要拜访哪里？",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text("从校园地标，到相遇的每一个人。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    query,
                    { query = it },
                    Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("搜索组织 · 支持国服与民间译名") },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                )
                SectionStatus(state) { vm.loadCatalog(true) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${entries.size} 个组织",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    IconButton({ vm.loadCatalog(true) }, enabled = !state.loading) {
                        Icon(Icons.Outlined.Refresh, "刷新组织目录")
                    }
                    IconToggleButton(compact, vm::compact, Modifier.testTag("organization_view")) {
                        Icon(
                            if (compact) Icons.Outlined.GridView else Icons.Outlined.ViewList,
                            if (compact) "切换卡片视图" else "切换紧凑列表视图",
                        )
                    }
                }
            }
        }
        items(entries, key = { it.id }) { organization ->
            KivoCard {
                if (compact) {
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable { open(organization.card(translation)) }
                            .testTag("organization_${organization.id}")
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        KivoImage(
                            organization.logo ?: organization.cover,
                            null,
                            Modifier.size(40.dp),
                            ContentScale.Fit,
                        )
                        Text(
                            organization.name.display(translation),
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Icon(
                            Icons.Outlined.ChevronRight,
                            null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Column(
                        Modifier.fillMaxWidth()
                            .clickable { open(organization.card(translation)) }
                            .testTag("organization_${organization.id}")
                    ) {
                        KivoImage(
                            organization.cover ?: organization.logo,
                            null,
                            Modifier.fillMaxWidth().aspectRatio(1.5f),
                            ContentScale.Crop,
                        )
                        Row(
                            Modifier.padding(horizontal = 8.dp, vertical = 10.dp)
                                .heightIn(min = 36.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                organization.name.display(translation),
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                if (!state.loading && entries.isEmpty())
                    Text(if (query.isBlank()) "暂无组织资料" else "已加载的组织中没有匹配结果")
                if (vm.canLoadMore)
                    TextButton({ vm.page() }, enabled = !state.loading) { Text("加载更多组织") }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun OrganizationDetailScreen(
    entity: EntityKey,
    vm: OrganizationViewModel,
    bookmarked: Boolean,
    open: (ContentCard) -> Unit,
    website: (String) -> Unit,
) {
    var retry by rememberSaveable(entity) { mutableIntStateOf(0) }
    val schoolState by
        produceState(
            LoadState<OrganizationProfile>(loading = entity.type == EntityType.SCHOOL),
            entity,
            retry,
        ) {
            if (entity.type == EntityType.SCHOOL)
                vm.repository.school(entity.id, retry > 0).collect { value = it }
        }
    val relationState by
        produceState(
            LoadState<RelationProfile>(loading = entity.type == EntityType.RELATION),
            entity,
            retry,
        ) {
            if (entity.type == EntityType.RELATION)
                vm.repository.relation(entity.id, retry > 0).collect { value = it }
        }
    val school = schoolState.value
    val relation = relationState.value
    val translation = LocalKivoSettings.current.translation
    val card = school?.card(translation) ?: relation?.card(translation)
    val source = school?.description ?: relation?.description.orEmpty()
    val blocks by
        produceState<List<ContentBlock>>(emptyList(), source) {
            value = withContext(Dispatchers.Default) { ContentParser.parse(source).readingBlocks() }
        }
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var section by rememberSaveable(entity) { mutableIntStateOf(0) }
    var image by rememberSaveable(entity) { mutableStateOf<String?>(null) }
    var toc by rememberSaveable(entity) { mutableStateOf(false) }
    var headerHeight by remember { mutableIntStateOf(0) }
    val sections =
        if (school != null) listOf("概览", "地图地标", "组织资料", "所属关系") else listOf("成员", "关系资料")
    fun link(url: String) {
        UrlPolicy.entity(url)?.let { open(ContentCard(it, "")) } ?: website(url)
    }
    // 只节流记录停稳后的阅读位置；导航返回时 Compose 保存列表状态，不按像素写数据库。
    LaunchedEffect(card, list) {
        if (card != null)
            snapshotFlow { list.isScrollInProgress }
                .collect { moving ->
                    if (!moving)
                        vm.record(
                            card,
                            list.firstVisibleItemIndex,
                            list.firstVisibleItemScrollOffset,
                        )
                }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            Modifier.widthIn(max = 1000.dp).fillMaxSize().testTag("organization_detail"),
            state = list,
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item("header") {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (entity.type == EntityType.SCHOOL) SectionStatus(schoolState) { retry++ }
                    else SectionStatus(relationState) { retry++ }
                    if (card != null) {
                        school?.cover?.let {
                            KivoImage(
                                it,
                                null,
                                Modifier.fillMaxWidth()
                                    .height(170.dp)
                                    .clip(RoundedCornerShape(20.dp)),
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            KivoImage(
                                card.image,
                                null,
                                Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)),
                                ContentScale.Fit,
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (school != null) "组织笔记" else "关系档案",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                                Text(
                                    card.title,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            IconButton({ vm.bookmark(card, bookmarked) }) {
                                Icon(
                                    if (bookmarked) Icons.Outlined.Bookmark
                                    else Icons.Outlined.BookmarkBorder,
                                    if (bookmarked) "取消本地收藏" else "收藏资料",
                                )
                            }
                        }
                        TextButton({ retry++ }) { Text("刷新资料") }
                    }
                }
            }
            if (card != null) {
                stickyHeader("sections") {
                    ScrollableTabRow(
                        section.coerceIn(sections.indices),
                        edgePadding = 8.dp,
                        modifier = Modifier.onSizeChanged { headerHeight = it.height },
                    ) {
                        sections.forEachIndexed { index, title ->
                            Tab(
                                section == index,
                                {
                                    list.requestScrollToItem(
                                        list.firstVisibleItemIndex.coerceAtMost(1),
                                        if (list.firstVisibleItemIndex <= 1)
                                            list.firstVisibleItemScrollOffset
                                        else 0,
                                    )
                                    section = index
                                },
                                text = { Text(title) },
                                modifier = Modifier.testTag("organization_section_$index"),
                            )
                        }
                    }
                }
                if (school != null)
                    when (section) {
                        0 -> {
                            item("overview") {
                                Padded {
                                    Text(
                                        "${school.students.size} 位角色 · ${school.relations.size} 项关系 · ${school.maps.size} 张地图",
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    Text("组织所属角色", style = MaterialTheme.typography.titleLarge)
                                    MemberStrip(school.students, open)
                                    Text(
                                        "沿着地图认识校园，或打开资料阅读组织的故事。",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton({ section = 1 }) { Text("探索地图") }
                                        Button({ section = 2 }) { Text("阅读组织资料") }
                                    }
                                }
                            }
                        }
                        1 -> {
                            if (school.maps.isEmpty()) item { Padded { Text("该组织暂无已收录地图") } }
                            itemsIndexed(school.maps, key = { index, _ -> "map_$index" }) { _, map
                                ->
                                Padded { OrganizationMapCard(map, ::link, { image = it }) }
                            }
                        }
                        2 -> markdownItems(blocks, source, ::link, { image = it }, { toc = true })
                        3 -> {
                            if (school.relations.isEmpty()) item { Padded { Text("暂无已收录所属关系") } }
                            items(school.relations, key = { "relation_$it" }) { id ->
                                Padded { RelationGroup(id, vm.repository, open) }
                            }
                        }
                    }
                else if (relation != null) {
                    if (section == 0) {
                        item("main") {
                            Padded {
                                Text("主要归属角色", style = MaterialTheme.typography.titleLarge)
                                MemberStrip(relation.mainStudents, open)
                            }
                        }
                        item("secondary") {
                            Padded {
                                Text("次要关联角色", style = MaterialTheme.typography.titleLarge)
                                MemberStrip(relation.secondaryStudents, open)
                            }
                        }
                    } else markdownItems(blocks, source, ::link, { image = it }, { toc = true })
                }
            }
        }
    }
    if (toc)
        ModalBottomSheet(
            { toc = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            val headings = blocks.mapIndexedNotNull { index, block ->
                block.readingHeading()?.let { index to it }
            }
            LazyColumn(
                Modifier.fillMaxWidth().testTag("organization_toc"),
                contentPadding = PaddingValues(20.dp),
            ) {
                item { Text("资料目录", style = MaterialTheme.typography.titleLarge) }
                if (headings.isEmpty()) item { Text("这份资料没有标题目录") }
                items(headings, key = { it.first }) { (index, block) ->
                    TextButton(
                        {
                            toc = false
                            scope.launch { list.animateScrollToItem(index + 3, -headerHeight) }
                        },
                        modifier =
                            Modifier.fillMaxWidth()
                                .testTag("organization_heading_$index")
                                .padding(start = ((block.heading - 1) * 12).dp),
                    ) {
                        Text(block.spans.joinToString("") { it.text }, Modifier.fillMaxWidth())
                    }
                }
            }
        }
    image?.let { OrganizationImageDialog(it, vm.assets) { image = null } }
}

private fun LazyListScope.markdownItems(
    blocks: List<ContentBlock>,
    source: String,
    link: (String) -> Unit,
    image: (String) -> Unit,
    toc: () -> Unit,
) {
    item("toc") {
        Padded { if (source.isBlank()) Text("这份资料尚待补充") else TextButton(toc) { Text("资料目录") } }
    }
    itemsIndexed(blocks, key = { index, _ -> "body_$index" }) { _, block ->
        Padded { ContentBlockView(block, link, image) }
    }
}

@Composable
internal fun Padded(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}
