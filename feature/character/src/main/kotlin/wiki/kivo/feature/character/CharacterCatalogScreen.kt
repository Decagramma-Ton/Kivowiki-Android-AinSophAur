package wiki.kivo.feature.character

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import wiki.kivo.core.designsystem.*
import wiki.kivo.core.model.*

@Composable
fun CharacterCatalogScreen(
    open: (ContentCard) -> Unit,
    vm: CatalogViewModel,
    scrollToTopRequest: Int = 0,
) {
    val result by vm.state.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val organizations by vm.schools.collectAsStateWithLifecycle()
    val settings = LocalKivoSettings.current
    val compact = settings.compactCatalog
    var filters by rememberSaveable { mutableStateOf(false) }
    var sort by rememberSaveable { mutableStateOf(false) }
    val grid = rememberLazyGridState()
    val dragged by grid.interactionSource.collectIsDraggedAsState()
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    // LazyGrid 的可保存状态会跨底栏切换恢复。只有真正的拖动才记为“用户看过此位置”，
    // 避免首次数据填充、尺寸重算或旧保存状态把图鉴停在页尾。
    var userScrolled by rememberSaveable { mutableStateOf(false) }
    var handledTopRequest by rememberSaveable { mutableIntStateOf(scrollToTopRequest) }
    LaunchedEffect(dragged) {
        if (dragged) userScrolled = true
    }
    // 返回详情时保留用户滚动位置；查询本身发生变化则回到首行。
    var previousQuery by rememberSaveable { mutableStateOf(query.toString()) }
    LaunchedEffect(query) {
        if (previousQuery != query.toString()) {
            grid.scrollToItem(0)
            userScrolled = false
            previousQuery = query.toString()
        }
    }
    LaunchedEffect(result.value?.entries?.isNotEmpty(), userScrolled) {
        if (result.value?.entries?.isNotEmpty() == true && !userScrolled) grid.scrollToItem(0)
    }
    LaunchedEffect(scrollToTopRequest) {
        if (handledTopRequest != scrollToTopRequest) {
            handledTopRequest = scrollToTopRequest
            userScrolled = false
            grid.animateScrollToItem(0)
        }
    }
    Box(Modifier.fillMaxSize().testTag("student_catalog")) {
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "角色图鉴",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "KIVOTOS ARCHIVE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                OutlinedTextField(
                    query.search,
                    vm::search,
                    Modifier.fillMaxWidth().testTag("catalog_search"),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions =
                        KeyboardActions(
                            onSearch = {
                                keyboard?.hide()
                                focusManager.clearFocus()
                            }
                        ),
                    placeholder = { Text("搜索姓名、别名或换装") },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    trailingIcon = {
                        if (query.search.isNotEmpty())
                            IconButton({ vm.search("") }) { Icon(Icons.Outlined.Close, "清空搜索") }
                    },
                    shape = RoundedCornerShape(16.dp),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        filters || query.filters.isNotEmpty(),
                        { filters = true },
                        label = {
                            Text(
                                "筛选${if(query.filters.isEmpty()) "" else " · ${query.filters.size}"}"
                            )
                        },
                        leadingIcon = { Icon(Icons.Outlined.Tune, null, Modifier.size(18.dp)) },
                        modifier = Modifier.testTag("catalog_filters"),
                    )
                    TextButton({ sort = true }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.Sort, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            CharacterFilters.sorts.find { it.first == query.sort }!!.second +
                                if (query.descending) " ↓" else " ↑",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconToggleButton(
                        compact,
                        { vm.compact(it) },
                        Modifier.testTag("catalog_view"),
                    ) {
                        Icon(
                            if (compact) Icons.Outlined.GridView
                            else Icons.AutoMirrored.Outlined.ViewList,
                            if (compact) "切换卡片视图" else "切换紧凑列表",
                        )
                    }
                }
                // 组织是最常用的筛选维度，不必打开完整面板即可单击切换。
                LazyRow(
                    modifier = Modifier.fillMaxWidth().testTag("catalog_school_quick_filter"),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    item("school-label") {
                        Text(
                            "所属组织",
                            Modifier.heightIn(min = 48.dp)
                                .wrapContentHeight(Alignment.CenterVertically),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    item("school-all") {
                        FilterChip(
                            selected = query.filters["school"].isNullOrBlank(),
                            onClick = {
                                vm.update(query.copy(filters = query.filters - "school"))
                            },
                            label = { Text("全部") },
                        )
                    }
                    items(organizations.value.orEmpty(), key = { "school-${it.id}" }) { school ->
                        val selected = query.filters["school"] == school.id.toString()
                        FilterChip(
                            selected = selected,
                            onClick = {
                                vm.update(
                                    query.copy(
                                        filters =
                                            if (selected) query.filters - "school"
                                            else query.filters + ("school" to school.id.toString())
                                    )
                                )
                            },
                            label = { Text(school.name.display(settings.translation)) },
                        )
                    }
                }
                if (query.filters.isNotEmpty())
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(query.filters.entries.toList(), key = { it.key }) { (key, value) ->
                            val label =
                                if (key == "school")
                                    organizations.value
                                        ?.find { it.id.toString() == value }
                                        ?.name
                                        ?.display(settings.translation) ?: value
                                else CharacterFilters.label(key, value)
                            InputChip(
                                true,
                                { vm.update(query.copy(filters = query.filters - key)) },
                                label = {
                                    Text(
                                        "${CharacterFilters.all.find{it.key==key}!!.label} · $label"
                                    )
                                },
                                trailingIcon = {
                                    Icon(Icons.Outlined.Close, "移除此筛选", Modifier.size(14.dp))
                                },
                            )
                        }
                    }
                SectionStatus(result) { vm.load(true) }
            }
            val entries = result.value?.entries.orEmpty()
            LazyVerticalGrid(
                if (compact) GridCells.Fixed(1)
                else
                    GridCells.Adaptive(
                        (88f * LocalDensity.current.fontScale.coerceIn(1f, 1.5f)).dp
                    ),
                Modifier.weight(1f).testTag("catalog_grid"),
                state = grid,
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(
                    entries,
                    key = { it.student.id },
                    contentType = { if (compact) "compact" else "card" },
                ) { entry ->
                    val student = entry.student.translated(settings.translation)
                    val school =
                        organizations.value
                            ?.find { it.id == entry.school }
                            ?.name
                            ?.display(settings.translation)
                            .orEmpty()
                    KivoCard(
                        modifier = Modifier.testTag("catalog_character_${student.id}"),
                        onClick = { open(student.card()) },
                    ) {
                        if (compact)
                            Row(
                                Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                KivoImage(
                                    student.avatar,
                                    student.displayName,
                                    Modifier.size(62.dp).clip(RoundedCornerShape(12.dp)),
                                    ContentScale.Crop,
                                )
                                Column(
                                    Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(
                                        student.displayName,
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    Text(
                                        school.ifBlank { "组织待补充" },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text(
                                    "${student.id.toString().padStart(3,'0')}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        else
                            Column(
                                Modifier.padding(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                KivoImage(
                                    student.avatar,
                                    student.displayName,
                                    Modifier.fillMaxWidth()
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(12.dp)),
                                    ContentScale.Crop,
                                )
                                Text(
                                    student.displayName,
                                    style = MaterialTheme.typography.titleSmall,
                                    minLines = 2,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                    }
                }
                item("footer", span = { GridItemSpan(maxLineSpan) }) {
                    when {
                        result.loading ->
                            Box(
                                Modifier.fillMaxWidth().padding(24.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(Modifier.size(28.dp))
                            }
                        result.error != null ->
                            OutlinedButton(
                                { vm.load(entries.isEmpty()) },
                                Modifier.fillMaxWidth(),
                            ) {
                                Text("重新读取")
                            }
                        entries.isEmpty() ->
                            Column(
                                Modifier.fillMaxWidth().padding(vertical = 36.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(Icons.Outlined.SearchOff, null, Modifier.size(48.dp))
                                Text("没有符合条件的角色")
                                TextButton({ vm.update(CharacterQuery()) }) { Text("重置搜索和筛选") }
                            }
                        vm.more() ->
                            OutlinedButton(
                                { vm.load() },
                                Modifier.fillMaxWidth().testTag("catalog_more"),
                            ) {
                                Text("继续加载")
                            }
                        else ->
                            TextButton({ vm.load(true) }, Modifier.fillMaxWidth()) {
                                Text("已浏览全部结果 · 刷新")
                            }
                    }
                }
            }
        }
        if (grid.firstVisibleItemIndex > 0 || grid.firstVisibleItemScrollOffset > 240)
            FilledTonalIconButton(
                onClick = {
                    userScrolled = false
                    scope.launch { grid.animateScrollToItem(0) }
                },
                modifier =
                    Modifier.align(Alignment.BottomEnd)
                        .padding(20.dp)
                        .testTag("catalog_back_to_top"),
            ) {
                Icon(Icons.Outlined.KeyboardArrowUp, "回到图鉴顶部")
            }
    }
    if (filters)
        CharacterFilterSheet(query, organizations, { vm.loadSchools() }, { filters = false }) {
            vm.update(it)
            filters = false
        }
    if (sort)
        SortDialog(query, { sort = false }) {
            vm.update(it)
            sort = false
        }
}

@Composable
private fun SortDialog(
    query: CharacterQuery,
    dismiss: () -> Unit,
    apply: (CharacterQuery) -> Unit,
) {
    var selected by remember { mutableStateOf(query.sort) }
    var descending by remember { mutableStateOf(query.descending) }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("结果排序") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                CharacterFilters.sorts.forEach { (key, label) ->
                    Row(
                        Modifier.fillMaxWidth().clickable { selected = key },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected == key, { selected = key })
                        Text(label)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("降序", Modifier.weight(1f))
                    Switch(descending, { descending = it })
                }
            }
        },
        confirmButton = {
            TextButton({ apply(query.copy(sort = selected, descending = descending)) }) {
                Text("应用")
            }
        },
        dismissButton = { TextButton(dismiss) { Text("取消") } },
    )
}
