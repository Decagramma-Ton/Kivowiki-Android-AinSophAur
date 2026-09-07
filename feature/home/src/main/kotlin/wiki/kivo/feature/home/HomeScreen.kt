package wiki.kivo.feature.home

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch
import wiki.kivo.core.designsystem.*
import wiki.kivo.core.model.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    now: Long,
    onServer: (GameServer) -> Unit,
    onRefresh: () -> Unit,
    onOpen: (ContentCard) -> Unit,
    onBulletins: () -> Unit,
    onPortal: (String) -> Unit,
) {
    val settings = LocalKivoSettings.current
    val scroll = rememberLazyListState()
    var newsList by rememberSaveable { mutableStateOf(false) }
    var anniversaryList by rememberSaveable { mutableStateOf(false) }
    var scheduleDetail by rememberSaveable { mutableStateOf<String?>(null) }
    BoxWithConstraints(
        Modifier.fillMaxSize().testTag("home_screen"),
        contentAlignment = Alignment.TopCenter,
    ) {
        val wide = maxWidth >= 1000.dp
        val pagePadding = if (maxWidth >= 600.dp) 28.dp else 20.dp
        Row(
            Modifier.widthIn(max = 1200.dp).fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
        ) {
            LazyColumn(
                state = scroll,
                modifier = Modifier.weight(1f).widthIn(max = 820.dp).testTag("home_feed"),
                contentPadding =
                    PaddingValues(
                        start = pagePadding,
                        end = pagePadding,
                        top = 12.dp,
                        bottom = 32.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                item("greeting") { Greeting() }
                item("news") {
                    Column(
                        modifier = Modifier.testTag("home_news"),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        NewsHero(state.news.value.orEmpty(), onOpen)
                        if (
                            state.news.value == null ||
                                state.news.error != null ||
                                state.news.loading
                        )
                            SectionStatus(state.news, onRefresh)
                        if (!state.news.value.isNullOrEmpty())
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    state.news.fetchedAt?.let { "更新于 ${shortDate(it)}" }
                                        ?: "今天的馆内资讯",
                                    Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                TextButton(onClick = { newsList = true }) {
                                    Text("更多资讯", style = MaterialTheme.typography.labelMedium)
                                    Icon(
                                        Icons.AutoMirrored.Outlined.ArrowForward,
                                        null,
                                        Modifier.size(14.dp),
                                    )
                                }
                            }
                    }
                }
                item("recent") {
                    Column(
                        modifier = Modifier.testTag("home_recent"),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        SectionTitle("近期编辑", "RECENTLY UPDATED")
                        SectionStatus(state.recent, onRefresh)
                        StudentShelf(state.recent.value.orEmpty(), false, onOpen)
                    }
                }
                if (settings.showBirthdays)
                    item("birthdays") {
                        Column(
                            modifier = Modifier.testTag("home_birthdays"),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            SectionTitle("本周生日", "HAPPY BIRTHDAY")
                            SectionStatus(state.birthdays, onRefresh)
                            StudentShelf(state.birthdays.value.orEmpty(), true, onOpen)
                            if (state.birthdays.value?.isEmpty() == true)
                                Text(
                                    "本周暂无生日记录，期待下一次相遇。",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                        }
                    }
                if (!wide)
                    item("schedule") {
                        SchedulePanel(state, now, onServer, onRefresh) { scheduleDetail = it }
                    }
                item("bulletin") {
                    BulletinStrip(state.bulletin.value?.entries?.firstOrNull()?.title, onBulletins)
                }
                item("articles") {
                    Column {
                        SectionTitle("最新文章", "FROM THE ARCHIVE", "报刊亭") { onPortal("articles") }
                        SectionStatus(state.articles, onRefresh)
                        state.articles.value.orEmpty().take(3).forEachIndexed { index, card ->
                            ContentRow(card, onClick = { onOpen(card) })
                            if (index < 2)
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                        if (state.articles.value?.isEmpty() == true) StatusNote("近期还没有文章")
                    }
                }
                if (!wide) item("lucky") { LuckyCard(state.lucky, onOpen, onRefresh) }
                if (settings.showHistory)
                    item("anniversary") {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            SectionTitle("那年今日", "ON THIS DAY", "更多回忆") { anniversaryList = true }
                            Text(
                                "近五年选读 · 以北京时间的今天为准",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            SectionStatus(state.anniversary, onRefresh)
                            state.anniversary.value.orEmpty().take(3).forEach { card ->
                                TimelineRow(card) { onOpen(card) }
                            }
                            if (state.anniversary.value?.isEmpty() == true) StatusNote("今天暂时没有历史记录")
                        }
                    }
                item("footer") {
                    Column(
                        Modifier.fillMaxWidth().padding(top = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        ArchiveMark(
                            Modifier.size(26.dp),
                            MaterialTheme.colorScheme.outline.copy(alpha = .5f),
                        )
                        Text(
                            "记录基沃托斯的每一个瞬间",
                            Modifier.padding(top = 8.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = onRefresh) {
                            Icon(Icons.Outlined.Refresh, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("刷新首页")
                        }
                    }
                }
            }
            if (wide)
                LazyColumn(
                    Modifier.width(340.dp),
                    contentPadding = PaddingValues(top = 18.dp, end = 24.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    item { SchedulePanel(state, now, onServer, onRefresh) { scheduleDetail = it } }
                    item { LuckyCard(state.lucky, onOpen, onRefresh) }
                    item {
                        KivoCard {
                            Column(Modifier.padding(22.dp)) {
                                ArchiveMark()
                                Text(
                                    "随身的古书馆",
                                    Modifier.padding(top = 14.dp),
                                    style = MaterialTheme.typography.titleLarge,
                                )
                                Text(
                                    "在日常的一点空闲里，\n与基沃托斯再次相遇。",
                                    Modifier.padding(top = 10.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
        }
    }
    if (newsList || anniversaryList)
        ModalBottomSheet(
            onDismissRequest = {
                newsList = false
                anniversaryList = false
            }
        ) {
            LazyColumn(
                contentPadding = PaddingValues(24.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item {
                    Text(
                        if (newsList) "最新资讯" else "那年今日 · 历年选读",
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
                items(
                    if (newsList) state.news.value.orEmpty() else state.anniversary.value.orEmpty(),
                    key = { it.key.storageKey },
                ) { card ->
                    ContentRow(card) {
                        newsList = false
                        anniversaryList = false
                        onOpen(card)
                    }
                }
            }
        }
    scheduleDetail?.let { kind ->
        val schedule = state.schedules[kind]?.value
        ModalBottomSheet(onDismissRequest = { scheduleDetail = null }) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("${state.server.label} · $kind", style = MaterialTheme.typography.titleLarge)
                if (schedule != null) {
                    Text(
                        "${shortDate(schedule.start, state.server.zone)} — ${shortDate(schedule.end, state.server.zone)}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "时间显示为${if (state.server == GameServer.JP) "日本时间 UTC+9" else "北京时间 UTC+8"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (kind == "卡池")
                        StudentShelf(state.pickupStudents, false) {
                            scheduleDetail = null
                            onOpen(it)
                        }
                    if (schedule.banner != null)
                        KivoImage(
                            schedule.banner,
                            "$kind 横幅",
                            Modifier.fillMaxWidth()
                                .heightIn(min = 110.dp, max = 240.dp)
                                .aspectRatio(2.4f)
                                .clip(RoundedCornerShape(16.dp)),
                            ContentScale.Fit,
                        )
                    if (kind != "卡池" && schedule.banner == null) StatusNote("站点当前未提供这一期的横幅")
                }
                Text(
                    "日程以站点最近提供的资料为准。结束的记录会保留，并标明等待更新。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Greeting() {
    val date = LocalDate.now(ZoneId.of("Asia/Shanghai"))
    Column {
        if (
            LocalKivoSettings.current.festiveEffects && date.monthValue == 1 && date.dayOfMonth == 1
        )
            Text(
                "✦ 新的一年，也请多指教。",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
            )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
            Text(
                "  ${date.format(DateTimeFormatter.ofPattern("MM月dd日"))}  /  今日古书馆",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            "老师，欢迎回到古书馆。",
            Modifier.padding(top = 9.dp),
            style = MaterialTheme.typography.headlineMedium,
        )
    }
}

@Composable
private fun NewsHero(cards: List<ContentCard>, onOpen: (ContentCard) -> Unit) {
    if (cards.isEmpty()) return
    val pager = rememberPagerState(pageCount = { cards.size })
    val scope = rememberCoroutineScope()
    val reduce = LocalKivoSettings.current.reducedMotion
    val largeFont = LocalDensity.current.fontScale >= 1.5f
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HorizontalPager(pager, beyondViewportPageCount = 0, key = { cards[it].key.storageKey }) {
            index ->
            val card = cards[index]
            if (largeFont)
                KivoCard(Modifier.fillMaxWidth(), { onOpen(card) }) {
                    KivoImage(card.image, card.title, Modifier.fillMaxWidth().aspectRatio(2.2f))
                    Column(
                        Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            if (card.title.startsWith("[广告]")) "站内推广 · 广告" else "古书馆资讯",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(card.title, style = MaterialTheme.typography.titleLarge)
                    }
                }
            else
                Box(
                    Modifier.fillMaxWidth()
                        .aspectRatio(1.67f)
                        .clip(RoundedCornerShape(24.dp))
                        .clickable { onOpen(card) }
                ) {
                    KivoImage(card.image, card.title, Modifier.matchParentSize())
                    Box(
                        Modifier.matchParentSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color(0xC5102334)),
                                    startY = 80f,
                                )
                            )
                    )
                    Surface(
                        Modifier.align(Alignment.TopStart).padding(16.dp),
                        color = Color(0xEFFFFFFF),
                        shape = RoundedCornerShape(6.dp),
                    ) {
                        Text(
                            if (card.title.startsWith("[广告]")) "站内推广 · 广告" else "古书馆资讯",
                            Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                            color = Color(0xFF173B52),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    Text(
                        card.title,
                        Modifier.align(Alignment.BottomStart).padding(18.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
        }
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                repeat(cards.size) { i ->
                    Box(
                        Modifier.width(if (i == pager.currentPage) 22.dp else 5.dp)
                            .height(5.dp)
                            .clip(CircleShape)
                            .background(
                                if (i == pager.currentPage) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant
                            )
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${(pager.currentPage + 1).toString().padStart(2, '0')} / ${cards.size.toString().padStart(2, '0')}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                IconButton(
                    onClick = {
                        scope.launch {
                            val next = (pager.currentPage + 1) % cards.size
                            if (reduce) pager.scrollToPage(next)
                            else pager.animateScrollToPage(next)
                        }
                    },
                    Modifier.size(48.dp),
                ) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, "下一条资讯", Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun SchedulePanel(
    state: HomeUiState,
    now: Long,
    onServer: (GameServer) -> Unit,
    refresh: () -> Unit,
    open: (String) -> Unit,
) {
    val translation = LocalKivoSettings.current.translation
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("基沃托斯日程", "HAPPENING NOW")
        Row(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(4.dp)
        ) {
            GameServer.entries.forEach { server ->
                Surface(
                    onClick = { onServer(server) },
                    modifier =
                        Modifier.weight(1f).heightIn(min = 44.dp).testTag("server_${server.value}"),
                    color =
                        if (state.server == server) MaterialTheme.colorScheme.surface
                        else Color.Transparent,
                    shape = RoundedCornerShape(9.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            server.label,
                            style = MaterialTheme.typography.labelLarge,
                            color =
                                if (state.server == server) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        if (!state.server.hasScheduleApi) StatusNote("国际服日程接口暂未开放，后续接入后会在这里展示。")
        else
            KivoCard {
                listOf(
                        "卡池" to Icons.Outlined.AutoAwesome,
                        "活动" to Icons.Outlined.Festival,
                        "总力战" to Icons.Outlined.SportsEsports,
                    )
                    .forEachIndexed { index, (kind, icon) ->
                        val load = state.schedules[kind] ?: LoadState()
                        val schedule = load.value
                        Column(
                            Modifier.fillMaxWidth()
                                .clickable(enabled = schedule != null) { open(kind) }
                                .padding(17.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    icon,
                                    null,
                                    Modifier.size(21.dp),
                                    MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    kind,
                                    Modifier.padding(start = 10.dp).weight(1f),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    schedule?.status(now) ?: if (load.loading) "读取中" else "暂不可用",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Icon(
                                    Icons.Outlined.ChevronRight,
                                    null,
                                    Modifier.padding(start = 4.dp).size(16.dp),
                                    MaterialTheme.colorScheme.outline,
                                )
                            }
                            if (schedule != null) {
                                Text(
                                    "${shortDate(schedule.start, state.server.zone)} — ${shortDate(schedule.end, state.server.zone)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (schedule.status(now) == "进行中")
                                    LinearProgressIndicator(
                                        progress = { schedule.progress(now) },
                                        Modifier.fillMaxWidth().height(3.dp),
                                        color =
                                            MaterialTheme.colorScheme.primary.copy(alpha = .65f),
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                        drawStopIndicator = {},
                                    )
                                if (kind == "卡池" && state.pickupStudents.isNotEmpty())
                                    Text(
                                        state.pickupStudents.joinToString("  /  ") {
                                            it.translated(translation).displayName
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                            }
                            load.error?.let { StatusNote(it, "重试", refresh) }
                        }
                        if (index < 2)
                            HorizontalDivider(
                                Modifier.padding(horizontal = 18.dp),
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                    }
            }
        if (state.server.hasScheduleApi) {
            val stamp = state.schedules.values.mapNotNull { it.fetchedAt }.minOrNull()
            Text(
                "${if (state.server == GameServer.JP) "日本时间 UTC+9" else "北京时间 UTC+8"}  ·  ${if (stamp == null) "等待更新" else "获取于 ${shortDate(stamp, pattern = "MM.dd HH:mm")}"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BulletinStrip(title: String?, open: () -> Unit) {
    Surface(
        onClick = open,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .7f),
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.Campaign,
                null,
                Modifier.size(20.dp),
                MaterialTheme.colorScheme.primary,
            )
            Text(
                title ?: "馆内公告",
                Modifier.weight(1f).padding(horizontal = 10.dp),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(Icons.Outlined.ChevronRight, "阅读公告", Modifier.size(18.dp))
        }
    }
}

@Composable
private fun StudentShelf(students: List<Student>, birthday: Boolean, open: (ContentCard) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items(students, key = { it.id }) { original ->
            val student = original.translated(LocalKivoSettings.current.translation)
            Column(
                Modifier.width(86.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { open(student.card()) }
                    .padding(bottom = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                KivoImage(
                    student.avatar,
                    student.displayName,
                    Modifier.size(86.dp, 94.dp).clip(RoundedCornerShape(16.dp)),
                    ContentScale.Crop,
                )
                Text(
                    student.name,
                    Modifier.padding(top = 10.dp),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (birthday || student.skin.isNotBlank())
                    Text(
                        if (birthday) student.birthday.replace("-", ".") else student.skin,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
            }
        }
    }
}

@Composable
private fun LuckyCard(
    state: LoadState<ContentCard>,
    open: (ContentCard) -> Unit,
    refresh: () -> Unit,
) {
    KivoCard(onClick = state.value?.let { { open(it) } }) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.WbSunny,
                    null,
                    Modifier.size(20.dp),
                    MaterialTheme.colorScheme.tertiary,
                )
                Text(
                    "今日的一点好运",
                    Modifier.padding(start = 8.dp),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        state.value?.title ?: "好运正在路上…",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        "把这份小小的幸运带在身边。",
                        Modifier.padding(top = 6.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                KivoImage(
                    state.value?.image,
                    null,
                    Modifier.size(76.dp).clip(RoundedCornerShape(16.dp)),
                    ContentScale.Fit,
                )
            }
            SectionStatus(state, refresh)
        }
    }
}

@Composable
private fun TimelineRow(card: ContentCard, open: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = open)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            shortDate(card.timestamp, pattern = "yyyy"),
            Modifier.width(54.dp),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Box(
            Modifier.padding(top = 8.dp, end = 12.dp)
                .size(5.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
        Column(Modifier.weight(1f)) {
            Text(
                card.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (card.subtitle.isNotEmpty())
                Text(
                    card.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
        }
    }
}
