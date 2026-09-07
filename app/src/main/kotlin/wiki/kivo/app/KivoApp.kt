package wiki.kivo.app

import android.content.ActivityNotFoundException
import android.content.Intent
import android.view.WindowManager
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.*
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.*
import androidx.navigation3.ui.NavDisplay
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import wiki.kivo.app.navigation.*
import wiki.kivo.app.screens.*
import wiki.kivo.core.designsystem.*
import wiki.kivo.core.model.*
import wiki.kivo.feature.account.*
import wiki.kivo.feature.character.*
import wiki.kivo.feature.home.*
import wiki.kivo.feature.organization.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KivoApp(app: AppViewModel, pendingLink: StateFlow<String?>, consumed: () -> Unit) {
    val settings by app.settings.collectAsStateWithLifecycle()
    val home: HomeViewModel = viewModel()
    val account: AccountViewModel = viewModel()
    val homeState by home.state.collectAsStateWithLifecycle()
    val accountState by account.state.collectAsStateWithLifecycle()
    val bookmarks by account.bookmarks.collectAsStateWithLifecycle()
    val history by account.history.collectAsStateWithLifecycle()
    val cache by app.cacheSize.collectAsStateWithLifecycle()
    val external by pendingLink.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val largeFont = LocalDensity.current.fontScale >= 1.5f
    val activity = context as androidx.activity.ComponentActivity
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    // 不篡改图鉴的导航身份，用单调请求序号触发“再次点击回顶部”。
    var catalogTopRequest by rememberSaveable { mutableIntStateOf(0) }
    var sensitiveReader by remember { mutableStateOf(false) }
    val stacks =
        listOf(
            rememberNavBackStack(Root(0)),
            rememberNavBackStack(Root(1)),
            rememberNavBackStack(Root(2)),
            rememberNavBackStack(Root(3)),
            rememberNavBackStack(Root(4)),
        )
    val stack = stacks[tab]
    val rootStates = rememberSaveableStateHolder()
    fun navigate(key: NavKey) {
        if (stack.lastOrNull() != key) stack.add(key)
    }
    fun openCard(card: ContentCard) {
        navigate(Detail(card.key))
    }
    fun website(raw: String) {
        val url = UrlPolicy.link(raw)
        if (url == null) {
            scope.launch { snack.showSnackbar("这个链接无法安全打开") }
            return
        }
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        } catch (_: ActivityNotFoundException) {
            scope.launch { snack.showSnackbar("这台设备没有可用的浏览器") }
        }
    }
    var startupApplied by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(settings != null) {
        if (!startupApplied && settings != null) {
            startupApplied = true
            if (external == null) {
                val target = settings!!.startDestination
                tab = target.tab
                target.portal?.let { stacks[target.tab].add(Portal(it)) }
            }
        }
    }
    fun portal(id: String) {
        if (id == "students") tab = 2 else navigate(Portal(id))
    }
    fun searchResult(result: SearchResult) {
        result.entity?.let {
            navigate(Detail(it))
            return
        }
        val route =
            when (result.type) {
                "data_school" -> "data/organize"
                "comic" -> "comic"
                "gallery" -> "gallery"
                "music" -> "music"
                else -> null
            }
        if (route != null) website("https://kivo.wiki/$route/${result.id}")
        else scope.launch { snack.showSnackbar("这类资料暂未支持直接打开") }
    }
    val current = stack.lastOrNull()
    LaunchedEffect(external) {
        external?.let {
            parsePreviewLink(it)?.let { entity ->
                // 深链接可能先于 DataStore 就绪。消费链接后 external 会清空，必须同步
                // 标记已选定启动目标，避免稍后读到的默认图鉴偏好覆盖当前角色／文章。
                startupApplied = true
                tab = 0
                if (stacks[0].lastOrNull() != Detail(entity)) stacks[0].add(Detail(entity))
            }
            consumed()
        }
    }
    LaunchedEffect(accountState.user) {
        if (accountState.user != null && stack.lastOrNull() == Login) stack.removeLastOrNull()
    }
    LaunchedEffect(Unit) { app.messages.collect { snack.showSnackbar(it) } }
    DisposableEffect(current == Login || sensitiveReader) {
        if (current == Login || sensitiveReader)
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        home.refresh()
        if (current == Settings) app.measureCache()
    }
    LaunchedEffect(current) { if (current == Settings) app.measureCache() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var now by remember { mutableLongStateOf(home.repository.clock.now()) }
    LaunchedEffect(lifecycle, tab, current is Root) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                now = home.repository.clock.now()
                if (tab == 0 && current is Root) home.onClockTick()
                delay(60_000)
            }
        }
    }
    KivoTheme(settings ?: AppSettings()) {
        val dark = LocalKivoDark.current
        val view = LocalView.current
        SideEffect {
            WindowCompat.getInsetsController(activity.window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val rail = maxWidth >= 840.dp
            val labels = listOf("首页", "资料", "图鉴", "典藏", "我的")
            val icons =
                listOf(
                    Icons.Outlined.Home,
                    Icons.Outlined.Dashboard,
                    Icons.Outlined.Badge,
                    Icons.AutoMirrored.Outlined.MenuBook,
                    Icons.Outlined.PersonOutline,
                )
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                contentWindowInsets = WindowInsets.safeDrawing,
                topBar = {
                    TopAppBar(
                        title = {
                            if (current is Root)
                                Image(
                                    painterResource(
                                        wiki.kivo.core.designsystem.R.drawable.kivo_logo
                                    ),
                                    "kivo 古书馆",
                                    Modifier.width(if (largeFont) 112.dp else 132.dp).height(40.dp),
                                )
                            else
                                Text(
                                    when (current) {
                                        Settings -> "系统设置"
                                        Login -> "登录"
                                        Bulletins -> "馆内公告"
                                        About -> "关于古书馆"
                                        Diagnostics -> "状态预览"
                                        Search -> "全局搜索"
                                        is Community ->
                                            when (current.kind) {
                                                "contributor" -> "贡献者名单"
                                                "contact" -> "帮助我们"
                                                else -> "QQ频道"
                                            }
                                        is Detail -> current.entity.type.label
                                        is Library ->
                                            if (current.kind == "bookmarks") "本地收藏" else "阅读足迹"
                                        is Portal -> portalById(current.id).title
                                        else -> "古书馆"
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                )
                        },
                        navigationIcon = {
                            if (current !is Root)
                                IconButton(
                                    onClick = { stack.removeLastOrNull() },
                                    Modifier.testTag("back"),
                                ) {
                                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回")
                                }
                        },
                        actions = {
                            if (current != Search && current != Login) {
                                IconButton(
                                    onClick = { navigate(Search) },
                                    Modifier.testTag("open_search"),
                                ) {
                                    Icon(Icons.Outlined.Search, "全局搜索")
                                }
                            }
                            if (current is Root) {
                                IconButton(onClick = { navigate(Bulletins) }) {
                                    Icon(Icons.Outlined.NotificationsNone, "馆内公告")
                                }
                                IconButton(
                                    onClick = { navigate(Settings) },
                                    Modifier.testTag("open_settings"),
                                ) {
                                    Icon(Icons.Outlined.Tune, "系统设置")
                                }
                            }
                        },
                        colors =
                            TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.background
                            ),
                    )
                },
                bottomBar = {
                    if (!rail && current is Root)
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 0.dp,
                        ) {
                            labels.forEachIndexed { i, label ->
                                NavigationBarItem(
                                    selected = tab == i,
                                    onClick = {
                                        if (i == 2 && tab == i) catalogTopRequest++ else tab = i
                                    },
                                    icon = {
                                        if (i == 2)
                                            Surface(
                                                color =
                                                    if (tab == i) MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.primaryContainer,
                                                shape = RoundedCornerShape(15.dp),
                                                modifier = Modifier.offset(y = (-5).dp),
                                                shadowElevation = 2.dp,
                                            ) {
                                                Icon(
                                                    icons[i],
                                                    null,
                                                    Modifier.padding(11.dp).size(25.dp),
                                                    tint =
                                                        if (tab == i)
                                                            MaterialTheme.colorScheme.onPrimary
                                                        else MaterialTheme.colorScheme.primary,
                                                )
                                            }
                                        else Icon(icons[i], null)
                                    },
                                    label = { Text(label) },
                                    modifier = Modifier.testTag("tab_$i"),
                                    colors =
                                        NavigationBarItemDefaults.colors(
                                            indicatorColor =
                                                MaterialTheme.colorScheme.primaryContainer,
                                            selectedIconColor = MaterialTheme.colorScheme.primary,
                                            selectedTextColor = MaterialTheme.colorScheme.primary,
                                        ),
                                )
                            }
                        }
                },
                snackbarHost = { SnackbarHost(snack) },
            ) { padding ->
                Row(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                    if (rail)
                        NavigationRail(containerColor = MaterialTheme.colorScheme.background) {
                            Spacer(Modifier.height(20.dp))
                            labels.forEachIndexed { i, label ->
                                NavigationRailItem(
                                    selected = tab == i,
                                    onClick = {
                                        if (i == 2 && tab == i && current is Root)
                                            catalogTopRequest++
                                        else tab = i
                                    },
                                    icon = { Icon(icons[i], null) },
                                    label = { Text(label) },
                                    modifier = Modifier.testTag("tab_$i"),
                                )
                            }
                        }
                    // 每个根保留自己的返回栈；详情出栈时释放其 ViewModel 和网络协程。
                    rootStates.SaveableStateProvider(tab) {
                        NavDisplay(
                            backStack = stack,
                            modifier = Modifier.weight(1f),
                            onBack = { if (stack.size > 1) stack.removeLastOrNull() },
                            entryDecorators =
                                listOf(
                                    rememberSaveableStateHolderNavEntryDecorator(),
                                    rememberViewModelStoreNavEntryDecorator(),
                                ),
                            transitionSpec = {
                                fadeIn(
                                    animationSpec =
                                        androidx.compose.animation.core.tween(
                                            if (settings?.reducedMotion == true) 0 else 180
                                        )
                                ) togetherWith
                                    fadeOut(
                                        animationSpec =
                                            androidx.compose.animation.core.tween(
                                                if (settings?.reducedMotion == true) 0 else 120
                                            )
                                    )
                            },
                            entryProvider = { destination ->
                                NavEntry(destination) {
                                    when (destination) {
                                        is Root ->
                                            when (destination.tab) {
                                                0 ->
                                                    HomeScreen(
                                                        homeState,
                                                        now,
                                                        home::setServer,
                                                        { home.refresh(true) },
                                                        ::openCard,
                                                        { navigate(Bulletins) },
                                                        { portal(it) },
                                                    )
                                                1 -> PortalHub(false) { portal(it) }
                                                2 ->
                                                    CharacterCatalogScreen(
                                                        ::openCard,
                                                        viewModel(
                                                            factory =
                                                                activity
                                                                    .defaultViewModelProviderFactory
                                                        ),
                                                        catalogTopRequest,
                                                    )
                                                3 -> PortalHub(true) { portal(it) }
                                                else ->
                                                    ProfileScreen(
                                                        accountState,
                                                        bookmarks.size,
                                                        history.size,
                                                        {
                                                            account.account.dismissMessage()
                                                            navigate(Login)
                                                        },
                                                        { navigate(Library(it)) },
                                                        { navigate(Settings) },
                                                        { navigate(Bulletins) },
                                                        { navigate(About) },
                                                        account::logout,
                                                        account::revalidate,
                                                        { navigate(Community(it)) },
                                                    )
                                            }
                                        Settings ->
                                            SettingsScreen(
                                                settings ?: AppSettings(),
                                                account::theme,
                                                account::scale,
                                                account::flag,
                                                cache,
                                                app::clearCache,
                                                app::clearHistory,
                                                account::startDestination,
                                                account::translation,
                                                app::setCacheLimit,
                                            )
                                        Login ->
                                            LoginScreen(accountState, account::login, ::website)
                                        is Detail ->
                                            if (destination.entity.type == EntityType.STUDENT)
                                                CharacterDetailScreen(
                                                    destination.entity.id,
                                                    bookmarks.any {
                                                        it.card.key == destination.entity
                                                    },
                                                    ::openCard,
                                                    ::website,
                                                    { navigate(Login) },
                                                    { id, section ->
                                                        stack[stack.lastIndex] =
                                                            Detail(
                                                                EntityKey(EntityType.STUDENT, id),
                                                                section,
                                                            )
                                                    },
                                                    viewModel(
                                                        factory =
                                                            activity.defaultViewModelProviderFactory
                                                    ),
                                                    destination.characterTab,
                                                )
                                            else if (
                                                destination.entity.type in
                                                    setOf(EntityType.SCHOOL, EntityType.RELATION)
                                            )
                                                OrganizationDetailScreen(
                                                    destination.entity,
                                                    viewModel(
                                                        factory =
                                                            activity.defaultViewModelProviderFactory
                                                    ),
                                                    bookmarks.any {
                                                        it.card.key == destination.entity
                                                    },
                                                    ::openCard,
                                                    ::website,
                                                )
                                            else
                                                ReaderRoute(
                                                    destination.entity,
                                                    app,
                                                    bookmarks.any {
                                                        it.card.key == destination.entity
                                                    },
                                                    ::openCard,
                                                    ::website,
                                                    { navigate(Settings) },
                                                    { navigate(Login) },
                                                    { sensitiveReader = it },
                                                )
                                        Bulletins -> BulletinsRoute(app.repository, ::openCard)
                                        is Portal ->
                                            if (destination.id == "organization")
                                                OrganizationCatalogScreen(
                                                    viewModel(
                                                        factory =
                                                            activity.defaultViewModelProviderFactory
                                                    ),
                                                    ::openCard,
                                                )
                                            else PortalScreen(portalById(destination.id), ::website)
                                        is Library ->
                                            LibraryScreen(
                                                if (destination.kind == "bookmarks") bookmarks
                                                else history,
                                                destination.kind,
                                                ::openCard,
                                            )
                                        About ->
                                            AboutScreen(
                                                BuildConfig.VERSION_NAME,
                                                ::website,
                                                if (BuildConfig.DEBUG) {
                                                    { navigate(Diagnostics) }
                                                } else null,
                                            )
                                        Search -> SearchScreen(app.repository, ::searchResult)
                                        is Community ->
                                            if (destination.kind == "qq") QqChannelScreen()
                                            else
                                                CommunityScreen(destination.kind, ::website) {
                                                    navigate(Community("qq"))
                                                }
                                        Diagnostics -> if (BuildConfig.DEBUG) DiagnosticsScreen()
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}
