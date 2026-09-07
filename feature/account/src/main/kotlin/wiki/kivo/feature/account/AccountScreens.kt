package wiki.kivo.feature.account

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import wiki.kivo.core.designsystem.*
import wiki.kivo.core.model.*

@Composable
fun ProfileScreen(
    state: AccountState,
    bookmarkCount: Int,
    historyCount: Int,
    onLogin: () -> Unit,
    onLibrary: (String) -> Unit,
    onSettings: () -> Unit,
    onBulletins: () -> Unit,
    onAbout: () -> Unit,
    onLogout: () -> Unit,
    onRevalidate: () -> Unit,
    onCommunity: (String) -> Unit = {},
) {
    var confirmLogout by rememberSaveable { mutableStateOf(false) }
    LazyColumn(
        Modifier.fillMaxSize().testTag("profile_screen"),
        contentPadding = PaddingValues(22.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Column(Modifier.widthIn(max = 700.dp).fillMaxWidth()) {
                Text(
                    "MY ARCHIVE",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "我的古书馆",
                    Modifier.padding(top = 7.dp),
                    style = MaterialTheme.typography.headlineLarge,
                )
            }
        }
        item {
            KivoCard(
                Modifier.widthIn(max = 700.dp).fillMaxWidth(),
                onClick = if (state.user == null) onLogin else null,
            ) {
                Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(66.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (state.user?.avatar != null)
                                KivoImage(state.user?.avatar, "头像", Modifier.fillMaxSize())
                            else ArchiveMark(Modifier.size(42.dp))
                        }
                        Column(Modifier.weight(1f).padding(start = 16.dp)) {
                            Text(
                                state.user?.name ?: "未登录的老师",
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text(
                                if (state.user == null) "收藏与阅读，从这里继续。" else "欢迎回来，今天也辛苦啦。",
                                Modifier.padding(top = 5.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (state.user == null)
                        FilledTonalButton(
                            onClick = onLogin,
                            Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("open_login"),
                            enabled = !state.checking,
                        ) {
                            Text(if (state.checking) "恢复会话中…" else "登录古书馆账号")
                            Spacer(Modifier.width(8.dp))
                            Icon(
                                Icons.AutoMirrored.Outlined.ArrowForward,
                                null,
                                Modifier.size(18.dp),
                            )
                        }
                    else {
                        Text(
                            "古书馆 ID · ${state.user?.id}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(
                            onClick = onRevalidate,
                            enabled = !state.busy && !state.checking,
                        ) {
                            Text(if (state.checking) "正在验证会话…" else "验证登录状态")
                        }
                    }
                    state.message?.let { StatusNote(it) }
                }
            }
        }
        item {
            Row(
                Modifier.widthIn(max = 700.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                StatTile(
                    Modifier.weight(1f),
                    Icons.Outlined.BookmarkBorder,
                    bookmarkCount,
                    "本地收藏",
                ) {
                    onLibrary("bookmarks")
                }
                StatTile(Modifier.weight(1f), Icons.Outlined.History, historyCount, "阅读足迹") {
                    onLibrary("history")
                }
            }
        }
        item {
            Column(
                Modifier.widthIn(max = 700.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("古书馆日常", style = MaterialTheme.typography.titleMedium)
                KivoCard {
                    MenuRow(Icons.Outlined.Campaign, "馆内公告", "看看古书馆最近的变化", onClick = onBulletins)
                    HorizontalDivider(
                        Modifier.padding(horizontal = 20.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    MenuRow(Icons.Outlined.Tune, "系统设置", "外观、阅读与数据管理", onClick = onSettings)
                    HorizontalDivider(
                        Modifier.padding(horizontal = 20.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    MenuRow(Icons.Outlined.Info, "关于古书馆", "版本、隐私说明与反馈", onClick = onAbout)
                    HorizontalDivider(
                        Modifier.padding(horizontal = 20.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    MenuRow(Icons.Outlined.Groups, "贡献者名单", "记下每一份热爱与付出") {
                        onCommunity("contributor")
                    }
                    MenuRow(Icons.Outlined.FavoriteBorder, "帮助我们", "反馈、供稿与加入编辑组") {
                        onCommunity("contact")
                    }
                    MenuRow(Icons.Outlined.Forum, "QQ频道", "与老师们一起交流") { onCommunity("qq") }
                }
                Text(
                    "收藏与足迹保存在这台设备上，游客也可以使用。",
                    Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (state.user != null)
            item {
                OutlinedButton(
                    onClick = { confirmLogout = true },
                    enabled = !state.busy,
                    modifier = Modifier.widthIn(max = 700.dp).fillMaxWidth().testTag("logout"),
                ) {
                    Text(if (state.busy) "正在退出…" else "退出登录")
                }
            }
        item {
            Text(
                "每一次相遇，都值得被记录。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (confirmLogout)
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("退出古书馆账号？") },
            text = { Text("将清除这台设备上的登录会话。本地收藏和阅读足迹会保留。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmLogout = false
                        onLogout()
                    }
                ) {
                    Text("退出登录")
                }
            },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("取消") } },
        )
}

@Composable
private fun StatTile(
    modifier: Modifier,
    icon: ImageVector,
    count: Int,
    title: String,
    open: () -> Unit,
) {
    KivoCard(modifier, open) {
        Column(Modifier.padding(20.dp)) {
            Icon(icon, null, Modifier.size(24.dp), MaterialTheme.colorScheme.primary)
            Text(
                count.toString().padStart(2, '0'),
                Modifier.padding(top = 13.dp),
                style = MaterialTheme.typography.headlineLarge,
            )
            Text(
                title,
                Modifier.padding(top = 3.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun MenuRow(icon: ImageVector, title: String, description: String = "", onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(23.dp), MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (description.isNotBlank())
                Text(
                    description,
                    Modifier.padding(top = 3.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
        }
        Icon(
            Icons.Outlined.ChevronRight,
            null,
            Modifier.size(20.dp),
            MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
fun LoginScreen(
    state: AccountState,
    onLogin: (String, String) -> Unit,
    onWebsite: (String) -> Unit,
) {
    var account by rememberSaveable { mutableStateOf("") }
    // 密码仅存在于当前 Composition，Activity 重建时主动丢弃，不进入 SavedState。
    var password by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val submit = {
        keyboard?.hide()
        onLogin(account, password)
        password = ""
    }
    Column(
        Modifier.fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(28.dp)
            .testTag("login_screen"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier.widthIn(max = 440.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Spacer(Modifier.height(16.dp))
            ArchiveMark(Modifier.size(62.dp))
            Column {
                Text("欢迎回来，老师。", style = MaterialTheme.typography.headlineLarge)
                Text(
                    "登录你的古书馆账号，\n让我们的故事继续。",
                    Modifier.padding(top = 10.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedTextField(
                account,
                { account = it },
                Modifier.fillMaxWidth().testTag("account_input"),
                label = { Text("用户名或邮箱") },
                leadingIcon = { Icon(Icons.Outlined.PersonOutline, null) },
                singleLine = true,
                enabled = !state.busy,
                keyboardOptions =
                    KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Next),
                shape = RoundedCornerShape(14.dp),
            )
            OutlinedTextField(
                password,
                { password = it },
                Modifier.fillMaxWidth().testTag("password_input"),
                label = { Text("密码") },
                leadingIcon = { Icon(Icons.Outlined.LockOpen, null) },
                singleLine = true,
                enabled = !state.busy,
                visualTransformation =
                    if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { visible = !visible }) {
                        Icon(
                            if (visible) Icons.Outlined.VisibilityOff
                            else Icons.Outlined.Visibility,
                            if (visible) "隐藏密码" else "显示密码",
                        )
                    }
                },
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                keyboardActions =
                    KeyboardActions(
                        onDone = {
                            if (!state.busy && account.isNotBlank() && password.isNotEmpty())
                                submit()
                        }
                    ),
                shape = RoundedCornerShape(14.dp),
            )
            state.message?.let { StatusNote(it) }
            Button(
                onClick = submit,
                enabled = !state.busy && account.isNotBlank() && password.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("submit_login"),
                shape = RoundedCornerShape(14.dp),
            ) {
                if (state.busy) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                }
                Text(if (state.busy) "正在安全登录…" else "登录古书馆")
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { onWebsite("https://kivo.wiki/auth/register") }) {
                    Text("注册账号")
                }
                TextButton(onClick = { onWebsite("https://kivo.wiki/auth/reset_password") }) {
                    Text("忘记密码")
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(
                "使用古书馆的用户名或邮箱与密码。注册、找回密码和 QQ 登录可在网站完成；网站登录状态不会自动同步到 App。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = { onWebsite("https://kivo.wiki/auth/login") }) {
                Text("前往网站账号页")
                Icon(Icons.Outlined.OpenInNew, null, Modifier.padding(start = 8.dp).size(16.dp))
            }
        }
    }
}

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onTheme: (ThemeMode) -> Unit,
    onScale: (Float) -> Unit,
    onFlag: (String, Boolean) -> Unit,
    cacheBytes: Long?,
    onClearCache: () -> Unit,
    onClearHistory: () -> Unit,
    onStartDestination: (StartDestination) -> Unit = {},
    onTranslation: (TranslationMode) -> Unit = {},
    onCacheLimit: (CacheLimit) -> Unit = {},
) {
    var clearTarget by rememberSaveable { mutableStateOf<String?>(null) }
    LazyColumn(
        Modifier.fillMaxSize().testTag("settings_screen"),
        contentPadding = PaddingValues(22.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            SettingsGroup("启动时进入的位置", "下次全新启动时生效；返回 App 时保留正在阅读的位置") {
                FlowRow(
                    Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StartDestination.entries.forEach { target ->
                        FilterChip(
                            selected = settings.startDestination == target,
                            onClick = { onStartDestination(target) },
                            label = { Text(target.label) },
                            modifier = Modifier.testTag("start_${target.name}"),
                        )
                    }
                }
            }
        }
        item {
            SettingsGroup("语言设置", "角色姓名与装扮优先使用所选译名，缺少译名时使用现有资料") {
                FlowRow(
                    Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TranslationMode.entries.forEach { mode ->
                        FilterChip(
                            selected = settings.translation == mode,
                            onClick = { onTranslation(mode) },
                            label = { Text(mode.label) },
                            modifier = Modifier.testTag("translation_${mode.name}"),
                        )
                    }
                }
            }
        }
        item {
            SettingsGroup("资料与搜索") {
                SettingSwitch("默认以最高等级显示", "角色技能速览初始显示最高等级；关闭后从 1 级开始", settings.levelMax) {
                    onFlag("level_max", it)
                }
                SettingSwitch(
                    "搜索到匹配的结果时是否自动重定向",
                    "确认搜索后，仅在唯一精确匹配时直接打开",
                    settings.searchAutoRedirect,
                ) {
                    onFlag("search_auto_redirect", it)
                }
                SettingSwitch("启用节日特效", "节日期间显示轻量装饰，减少动效时保持静态", settings.festiveEffects) {
                    onFlag("festive_effects", it)
                }
            }
        }
        item {
            SettingsGroup("外观", "选择古书馆的明暗") {
                ThemeMode.entries.forEach { mode ->
                    Row(
                        Modifier.fillMaxWidth()
                            .selectable(
                                selected = settings.theme == mode,
                                role = Role.RadioButton,
                            ) {
                                onTheme(mode)
                            }
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                            .testTag("theme_${mode.name}"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = settings.theme == mode, onClick = null)
                        Icon(
                            when (mode) {
                                ThemeMode.SYSTEM -> Icons.Outlined.BrightnessAuto
                                ThemeMode.LIGHT -> Icons.Outlined.LightMode
                                ThemeMode.DARK -> Icons.Outlined.DarkMode
                            },
                            null,
                            Modifier.padding(start = 4.dp, end = 12.dp).size(21.dp),
                        )
                        Text(mode.label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
        item {
            SettingsGroup("阅读字号", "同时尊重系统的字体大小设置") {
                FlowRow(
                    Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(.9f, 1f, 1.15f, 1.3f, 1.5f).forEach { scale ->
                        FilterChip(
                            selected = settings.readingScale == scale,
                            onClick = { onScale(scale) },
                            label = {
                                Text(
                                    "${(scale * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                        )
                    }
                }
                Text(
                    "每一份记录，都藏着基沃托斯的故事。",
                    Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    fontSize = (17 * settings.readingScale).sp,
                    lineHeight = (28 * settings.readingScale).sp,
                )
            }
        }
        item {
            SettingsGroup("浏览体验") {
                SettingSwitch("左右滑动切换", "在角色信息页横向滑动切换数据、资料、鉴赏和语音", settings.swipeCategories) {
                    onFlag("swipe_categories", it)
                }
                SettingSwitch("显示图片", "关闭后以文字浏览，节省图片流量", settings.loadImages) {
                    onFlag("images", it)
                }
                SettingSwitch(
                    "显示角色背景",
                    "将已收录的回忆大厅静态图作为角色档案的淡化背景",
                    settings.showCharacterBackground,
                ) {
                    onFlag("character_background", it)
                }
                SettingSwitch("加载外部来源图片", "允许加载站点引用的外站图片", settings.externalImages) {
                    onFlag("external_images", it)
                }
                SettingSwitch("减少动画", "简化首页切换和阅读交互的动画", settings.reducedMotion) {
                    onFlag("reduce_motion", it)
                }
            }
        }
        item {
            SettingsGroup("Spine 动态预览", "立绘与回忆大厅共用；截图和录制沿用光照设置") {
                SettingSwitch("启用 GPU 渲染", "默认开启；出现显示异常时可关闭。旧系统自动使用兼容渲染", settings.spineGpu) {
                    onFlag("spine_gpu", it)
                }
                SettingSwitch("自动修复光照", "修正部分回忆大厅的叠加光效，默认开启", settings.spineFixBlend) {
                    onFlag("spine_fix_blend", it)
                }
                SettingSwitch(
                    "启用实验性渲染",
                    "立绘按素材原始比例显示，可能超出屏幕；可缩小或关闭此项",
                    settings.spineExperimental,
                ) {
                    onFlag("spine_experimental", it)
                }
            }
        }
        item {
            SettingsGroup("首页内容") {
                SettingSwitch("本周生日", "显示本周过生日的学生", settings.showBirthdays) {
                    onFlag("birthdays", it)
                }
                SettingSwitch("那年今日", "回顾历年今天的基沃托斯", settings.showHistory) {
                    onFlag("anniversary", it)
                }
            }
        }
        item {
            SettingsGroup("本地数据", "保存在这台设备上") {
                Text("缓存上限", Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
                FlowRow(
                    Modifier.padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CacheLimit.entries.forEach { limit ->
                        FilterChip(
                            selected = settings.cacheLimit == limit,
                            onClick = { onCacheLimit(limit) },
                            label = { Text(limit.label) },
                            modifier = Modifier.testTag("cache_${limit.name}"),
                        )
                    }
                }
                Text(
                    "包含图片、资料与动态素材；超限自动移除旧缓存，保留收藏与足迹。正在使用的素材会在关闭预览后清理。",
                    Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
                SettingSwitch("记录阅读足迹", "保存阅读位置，方便下次继续", settings.rememberHistory) {
                    onFlag("history", it)
                }
                MenuRow(
                    Icons.Outlined.CleaningServices,
                    "清理临时缓存",
                    cacheBytes?.let { "当前约 ${"%.1f".format(it / 1048576.0)} MB · 保留收藏与足迹" }
                        ?: "正在计算占用",
                ) {
                    clearTarget = "cache"
                }
                MenuRow(Icons.Outlined.History, "清空阅读足迹", "移除这台设备上的阅读记录") {
                    clearTarget = "history"
                }
            }
        }
        item {
            Text(
                "无广告追踪 SDK · 无强制后台运行",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    clearTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { clearTarget = null },
            title = { Text(if (target == "cache") "清理临时缓存？" else "清空阅读足迹？") },
            text = {
                Text(
                    if (target == "cache") "清理后需要联网重新获取资料和图片。本地收藏、阅读足迹和账号不会删除。"
                    else "此操作仅清除这台设备上的阅读记录和位置，收藏会保留。"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        clearTarget = null
                        if (target == "cache") onClearCache() else onClearHistory()
                    }
                ) {
                    Text("确认清理")
                }
            },
            dismissButton = { TextButton(onClick = { clearTarget = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun SettingsGroup(
    title: String,
    subtitle: String = "",
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier.widthIn(max = 700.dp).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (subtitle.isNotBlank())
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        KivoCard(Modifier.fillMaxWidth(), content = content)
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    description: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                description,
                Modifier.padding(top = 3.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked, onCheckedChange = onChange)
    }
}
