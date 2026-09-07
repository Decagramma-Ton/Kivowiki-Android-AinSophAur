package wiki.kivo.app.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import wiki.kivo.core.data.ContentRepository
import wiki.kivo.core.designsystem.*
import wiki.kivo.core.model.*

data class PortalInfo(
    val id: String,
    val title: String,
    val subtitle: String,
    val description: String,
    val url: String,
    val icon: ImageVector,
)

private val portals
    get() =
        listOf(
            PortalInfo(
                "students",
                "角色图鉴",
                "与每一位学生相遇",
                "角色档案、技能与养成资料、语音，以及立绘和模型鉴赏。",
                "https://kivo.wiki/data/character",
                Icons.Outlined.Badge,
            ),
            PortalInfo(
                "organization",
                "组织笔记",
                "基沃托斯的学院与社团",
                "学院、组织与关系资料，沿着角色之间的联系探索故事。",
                "https://kivo.wiki/data/organize",
                Icons.Outlined.AccountBalance,
            ),
            PortalInfo(
                "items",
                "物品仓库",
                "收藏日常里的小细节",
                "物品、礼物、家具和装备的说明与关联资料。",
                "https://kivo.wiki/data/item",
                Icons.Outlined.Inventory2,
            ),
            PortalInfo(
                "articles",
                "报刊亭",
                "翻开馆内的新知",
                "长文章、考据、访谈翻译与古书馆建设记录。首页的近期文章已支持原生阅读。",
                "https://kivo.wiki/article/list",
                Icons.AutoMirrored.Outlined.MenuBook,
            ),
            PortalInfo(
                "gallery",
                "画廊",
                "让每一帧回忆停留",
                "剧情 CG、角色参考和主题图集，后续提供分类与大图鉴赏。",
                "https://kivo.wiki/gallery",
                Icons.Outlined.Collections,
            ),
            PortalInfo(
                "music",
                "留声机",
                "听见属于这里的旋律",
                "音乐、歌词与曲目资料，后续提供原生播放器和后台播放。",
                "https://kivo.wiki/music",
                Icons.Outlined.Headphones,
            ),
            PortalInfo(
                "comics",
                "漫画屋",
                "在分镜之间，再见大家",
                "漫画合集与章节阅读，后续支持进度记忆和连贯阅读。",
                "https://kivo.wiki/comic",
                Icons.Outlined.AutoStories,
            ),
            PortalInfo(
                "timeline",
                "Kivo史书",
                "基沃托斯的时间刻度",
                "按日期、服别和类型回顾活动与故事。首页的那年今日已支持选读。",
                "https://kivo.wiki/timeline",
                Icons.Outlined.HistoryEdu,
            ),
            PortalInfo(
                "teams",
                "配队方案",
                "记录老师的每一个灵感",
                "浏览与整理配队思路，完整方案与编辑功能会在后续阶段接入。",
                "https://kivo.wiki/walkthrough",
                Icons.Outlined.Groups,
            ),
        )

fun portalById(id: String): PortalInfo = portals.firstOrNull { it.id == id } ?: portals.first()

@Composable
fun PortalHub(collection: Boolean, open: (String) -> Unit) {
    val entries = if (collection) portals.drop(3) else portals.take(3)
    LazyColumn(
        Modifier.fillMaxSize().testTag(if (collection) "collection_screen" else "catalog_screen"),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Column(Modifier.widthIn(max = 760.dp).fillMaxWidth().padding(bottom = 12.dp)) {
                Text(
                    if (collection) "THE COLLECTION" else "EXPLORE KIVOTOS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    if (collection) "把热爱，留在这里。" else "你好，基沃托斯。",
                    Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.headlineLarge,
                )
                Text(
                    if (collection) "文字、影像与旋律，都是值得珍藏的回忆。" else "从角色到学院，寻找故事里的每一处细节。",
                    Modifier.padding(top = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(entries, key = { it.id }) { entry ->
            KivoCard(Modifier.widthIn(max = 760.dp).fillMaxWidth(), { open(entry.id) }) {
                Row(Modifier.padding(22.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(58.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            entry.icon,
                            null,
                            Modifier.size(28.dp),
                            MaterialTheme.colorScheme.primary,
                        )
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 18.dp)) {
                        Text(entry.title, style = MaterialTheme.typography.titleLarge)
                        Text(
                            entry.subtitle,
                            Modifier.padding(top = 6.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        Icons.Outlined.ChevronRight,
                        null,
                        Modifier.size(22.dp),
                        MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }
        item {
            Text(
                "原生模块陆续接入中 · 现有内容可前往网站使用",
                Modifier.widthIn(max = 760.dp).padding(vertical = 14.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun PortalScreen(info: PortalInfo, website: (String) -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier.widthIn(max = 560.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Spacer(Modifier.height(20.dp))
            Box(
                Modifier.size(84.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(info.icon, null, Modifier.size(42.dp), MaterialTheme.colorScheme.primary)
            }
            Text(info.title, style = MaterialTheme.typography.headlineLarge)
            Text(info.subtitle, style = MaterialTheme.typography.titleLarge)
            Text(
                info.description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            StatusNote("完整原生模块将在后续版本接入。当前可通过下面的入口使用网站已有功能。")
            Button(
                onClick = { website(info.url) },
                Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text("在网站打开${info.title}")
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Outlined.OpenInNew, null, Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun LibraryScreen(entries: List<SavedEntry>, kind: String, open: (ContentCard) -> Unit) {
    if (entries.isEmpty())
        EmptyState(
            if (kind == "bookmarks") "把喜欢的资料留在这里" else "还没有留下阅读足迹",
            if (kind == "bookmarks") "打开资讯、公告或首页资料，点亮书签，就能随时回来。" else "读过的资料会出现在这里，也会记住上次停下的位置。",
        )
    else
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Text(
                    "本机保存 · ${entries.size} 条",
                    Modifier.widthIn(max = 720.dp).fillMaxWidth(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(entries, key = { it.card.key.storageKey }) { entry ->
                Column(Modifier.widthIn(max = 720.dp)) {
                    ContentRow(entry.card) { open(entry.card) }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
}

data class BulletinState(
    val loaded: LoadState<Page<ContentCard>> = LoadState(),
    val entries: List<ContentCard> = emptyList(),
    val ended: Boolean = false,
)

/** 分页在 ViewModel 内合并，旋转屏幕不会丢掉前页；缓存与网络的同页两次发射不算重复页。 */
class BulletinViewModel(private val repository: ContentRepository) : ViewModel() {
    private val mutable = MutableStateFlow(BulletinState())
    val state = mutable.asStateFlow()
    private val pages = sortedMapOf<Int, List<ContentCard>>()
    private var page = 1
    private var job: Job? = null

    init {
        load(false)
    }

    fun retry() = load(true)

    fun next() {
        if (
            mutable.value.loaded.loading ||
                mutable.value.ended ||
                mutable.value.loaded.error != null
        )
            return
        page++
        load(false)
    }

    private fun load(force: Boolean) {
        job?.cancel()
        mutable.value =
            mutable.value.copy(loaded = mutable.value.loaded.copy(loading = true, error = null))
        job = viewModelScope.launch {
            repository.bulletins(page, force).collect { result ->
                var ended = mutable.value.ended
                result.value?.let { data ->
                    val before =
                        pages.filterKeys { it < page }.values.flatten().map { it.key }.toSet()
                    ended =
                        data.entries.isEmpty() ||
                            (page > 1 && data.entries.all { it.key in before }) ||
                            page >= data.maxPage
                    pages[page] = data.entries
                }
                mutable.value =
                    BulletinState(result, pages.values.flatten().distinctBy { it.key }, ended)
            }
        }
    }
}

@Composable
fun BulletinsRoute(repository: ContentRepository, open: (ContentCard) -> Unit) {
    val vm: BulletinViewModel =
        viewModel(factory = viewModelFactory { initializer { BulletinViewModel(repository) } })
    val state by vm.state.collectAsStateWithLifecycle()
    LazyColumn(
        Modifier.fillMaxSize().testTag("bulletins_screen"),
        contentPadding = PaddingValues(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(bottom = 20.dp)) {
                Text("来自古书馆的信", style = MaterialTheme.typography.headlineLarge)
                Text(
                    "站内动态、资料更新与建设记录。",
                    Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            Box(Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
                SectionStatus(state.loaded, vm::retry)
            }
        }
        items(state.entries, key = { it.key.storageKey }) { card ->
            Column(Modifier.widthIn(max = 720.dp)) {
                ContentRow(card) { open(card) }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
        item {
            if (!state.loaded.loading && !state.ended && state.loaded.error == null)
                TextButton(onClick = vm::next) { Text("更早的公告") }
            else if (state.ended)
                Text(
                    "已经读到最后一封信了",
                    Modifier.padding(22.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
        }
    }
}

@Composable
fun AboutScreen(version: String, website: (String) -> Unit, diagnostics: (() -> Unit)?) {
    val context = LocalContext.current
    var licenseFile by remember { mutableStateOf<String?>(null) }
    val licenseText by
        produceState("", licenseFile) {
            value =
                licenseFile
                    ?.let { name ->
                        withContext(Dispatchers.IO) {
                            runCatching {
                                    context.assets.open(name).bufferedReader().use { it.readText() }
                                }
                                .getOrDefault("许可正文暂时无法读取")
                        }
                    }
                    .orEmpty()
        }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier.widthIn(max = 620.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            ArchiveMark(Modifier.size(72.dp))
            Text("便携古书馆", style = MaterialTheme.typography.headlineLarge)
            Text("基沃托斯的故事，随身收藏。", style = MaterialTheme.typography.titleMedium)
            Text(
                "角色模块验收版 $version",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                "古书馆是《蔚蓝档案》的百科网站。本应用是由社区独立维护的非官方第三方安卓客户端，提供原生首页、图鉴搜索与筛选、完整角色档案、立绘和三维模型预览、角色语音、资料阅读及本地收藏。它不代表古书馆官方立场，也不经官方渠道发布。",
                style = MaterialTheme.typography.bodyLarge,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text("隐私与数据", style = MaterialTheme.typography.titleLarge)
            Text(
                "公开资料从 api.kivo.wiki 获取，图片按需从 static.kivo.wiki 及允许的来源加载。登录资料仅发送给古书馆 API；密码不会保存，刷新令牌使用 Android Keystore 加密保存。\n\n收藏、阅读足迹和设置保存在本机。应用不接入广告追踪或统计 SDK，不采集设备通讯录与位置。清理缓存不会删除收藏，退出登录会清除本机会话。卸载会移除本机数据。",
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = { website("https://kivo.wiki/license") }) { Text("网站使用条款与素材声明") }
            TextButton(onClick = { website("https://kivo.wiki/contact") }) { Text("联系古书馆与反馈") }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text("社区开发团队", style = MaterialTheme.typography.titleLarge)
            Text("Decagrammaton", style = MaterialTheme.typography.headlineMedium)
            Text("主要作者与项目维护", style = MaterialTheme.typography.bodyMedium)
            listOf(
                    "Kether" to "架构设计",
                    "Chokhmah" to "产品与交互",
                    "Binah" to "数据与 API",
                    "Chesed" to "界面与体验",
                    "Geburah" to "安全与隐私",
                    "Tiphereth" to "Android 工程",
                    "Netzach" to "媒体与渲染",
                    "Hod" to "质量保障",
                    "Yesod" to "文档维护",
                    "Malkuth" to "社区协作",
                    "Daath" to "构建与发布",
                )
                .forEach { (name, role) ->
                    Text(name, style = MaterialTheme.typography.titleMedium)
                    Text(role, style = MaterialTheme.typography.bodySmall)
                }
            TextButton(onClick = { website("https://github.com/Decagramma-Ton/Kivowiki-Android-AinSophAur") }) {
                Text("项目源代码仓库")
            }
            Text("第三方组件", style = MaterialTheme.typography.titleLarge)
            Text(
                "Kotlin、AndroidX / Compose、Navigation 3、Room、DataStore、Hilt、OkHttp、Retrofit、kotlinx.serialization、Coil、CommonMark、jsoup、Media3、Filament、Spine Runtimes。Spine 使用独立运行时许可，具体条款见下方正文。",
                style = MaterialTheme.typography.bodyMedium,
            )
            listOf(
                    "Apache 2.0" to "APACHE-2.0.txt",
                    "CommonMark" to "commonmark-BSD-2-Clause.txt",
                    "Mermaid" to "mermaid-LICENSE.txt",
                    "jsoup" to "jsoup-MIT.txt",
                    "autolink" to "autolink-MIT.txt",
                    "Spine Runtimes" to "licenses/Spine-Runtimes.txt",
                )
                .forEach { (title, file) ->
                    TextButton({ licenseFile = file }) { Text("$title 许可全文") }
                }
            Text(
                "本包使用独立验收身份和测试签名，尚未公开发行。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (diagnostics != null) OutlinedButton(onClick = diagnostics) { Text("开发验收：组件与状态") }
        }
    }
    if (licenseFile != null)
        AlertDialog(
            onDismissRequest = { licenseFile = null },
            title = { Text("许可正文") },
            text = {
                SelectionContainer {
                    Text(
                        licenseText,
                        Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = { TextButton({ licenseFile = null }) { Text("关闭") } },
        )
}

@Composable
fun DiagnosticsScreen() {
    var mode by remember { mutableIntStateOf(0) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("组件与异常状态", style = MaterialTheme.typography.headlineMedium)
        Text("仅 Debug 提供。下面是明确标记的本地状态演示，不会伪造网站数据或账号。", style = MaterialTheme.typography.bodyMedium)
        Row {
            listOf("加载", "空内容", "网络错误").forEachIndexed { index, title ->
                TextButton(onClick = { mode = index }) { Text(title) }
            }
        }
        when (mode) {
            0 -> SectionStatus(LoadState<String>()) {}
            1 -> EmptyState("暂时没有内容", "联网后可以再次刷新。")
            2 ->
                SectionStatus(LoadState<String>(loading = false, error = "网络连接暂时中断，已保留缓存")) {
                    mode = 0
                }
        }
        KivoCard {
            Column(Modifier.padding(20.dp)) {
                SectionTitle("长标题与浅深色预览")
                Text("这是一段用于检查大字号、换行与阅读节奏的示例文字。", style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
