package wiki.kivo.feature.character

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.*
import wiki.kivo.core.content.*
import wiki.kivo.core.data.CharacterRepository
import wiki.kivo.core.designsystem.*
import wiki.kivo.core.model.*

internal fun infoParts(
    profile: CharacterProfile,
    blocks: List<ContentBlock>,
    vm: CharacterViewModel,
    link: (String) -> Unit,
    image: (String) -> Unit,
    login: () -> Unit,
): List<DetailPart> = buildList {
    if (profile.more.isBlank()) add(DetailPart("empty-info", "角色资料") { Text("古书馆暂未收录更多文字资料。") })
    blocks.forEachIndexed { index, block ->
        val heading = block.readingHeading()?.spans?.joinToString("") { it.text }
        add(
            DetailPart("info-$index", heading, renderTitle = block !is ContentBlock.Aligned) {
                if (heading == null || block is ContentBlock.Aligned)
                    ContentBlockView(block, link, image)
            }
        )
    }
    if (profile.sources.isNotEmpty())
        add(DetailPart("sources", "资料来源") { CreditList(profile.sources, link) })
    if (profile.contributors.isNotEmpty())
        add(DetailPart("contributors", "本页贡献者") { CreditList(profile.contributors, link) })
    if (profile.supplementary.isNotBlank())
        add(
            DetailPart("supplements", "补充信息") {
                SupplementPanel(profile.supplementary, vm, link, image, login)
            }
        )
    if (profile.supplementaryDeclare.isNotBlank())
        add(
            DetailPart("supplement-reactions") {
                ReactionRow(profile.supplementaryDeclare, vm, login)
            }
        )
}

@Composable
private fun CreditList(credits: List<SourceCredit>, link: (String) -> Unit) {
    KivoCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            credits.forEachIndexed { index, credit ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${index+1}.", color = MaterialTheme.colorScheme.primary)
                    Text(
                        credit.text,
                        Modifier.weight(1f)
                            .then(
                                if (credit.url != null) Modifier.clickable { credit.url?.let(link) }
                                else Modifier
                            ),
                        style = MaterialTheme.typography.bodyMedium,
                        color =
                            if (credit.url != null) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
internal fun ReferenceRow(
    id: Int,
    relation: Boolean,
    translation: TranslationMode,
    repository: CharacterRepository,
    website: (ContentCard) -> Unit,
    displayLabel: String = if (relation) "社团 / 关系" else "所属组织",
    modifier: Modifier = Modifier,
) {
    if (id <= 0) return
    var retry by remember { mutableIntStateOf(0) }
    val state by
        produceState(LoadState<NamedReference>(), id, relation, retry) {
            (if (relation) repository.relation(id) else repository.school(id)).collect {
                value = it
            }
        }
    val label =
        state.value?.name?.display(translation) ?: if (state.error != null) "读取失败，点按重试" else "正在读取…"
    Row(
        modifier.fillMaxWidth().heightIn(min = 48.dp).clickable {
            if (state.value == null) retry++
            else
                website(
                    ContentCard(
                        EntityKey(if (relation) EntityType.RELATION else EntityType.SCHOOL, id),
                        label,
                        state.value?.image,
                    )
                )
        },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        KivoImage(state.value?.image, null, Modifier.size(30.dp))
        Column(Modifier.weight(1f)) {
            Text(
                displayLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Icon(Icons.Outlined.ChevronRight, null, Modifier.size(18.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReactionRow(
    uuid: String,
    vm: CharacterViewModel,
    login: () -> Unit,
    retainedController: ReactionController? = null,
) {
    if (uuid.isBlank()) return
    val account by vm.account.state.collectAsStateWithLifecycle()
    val controller = retainedController ?: rememberReactionController(uuid, vm)
    val state by controller.state.collectAsStateWithLifecycle()
    var expanded by rememberSaveable(uuid) { mutableStateOf(false) }
    fun react(id: Int) {
        if (account.user == null) login() else controller.toggle(id)
    }
    Column {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            state.value
                .orEmpty()
                .filter { it.count > 0 || it.selected }
                .sortedByDescending { it.count }
                .take(3)
                .forEach { reaction ->
                    FilterChip(
                        reaction.selected,
                        { react(reaction.id) },
                        enabled = !state.loading && !controller.uncertain,
                        label = { Text("${reaction.count}") },
                        leadingIcon = {
                            KivoImage(reaction.icon, "表情 ${reaction.id}", Modifier.size(25.dp))
                        },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            TextButton({ expanded = true }, enabled = state.value != null) {
                Icon(Icons.Outlined.AddReaction, null, Modifier.size(20.dp))
                Spacer(Modifier.width(4.dp))
                Text("表态")
            }
            if (state.loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        }
        SectionStatus(state) { controller.load() }
    }
    if (expanded)
        Dialog(
            { expanded = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(
                Modifier.padding(20.dp).widthIn(max = 560.dp).fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("老师的表态", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "再次点按已选表情可取消表态。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LazyVerticalGrid(
                        GridCells.Adaptive(68.dp),
                        Modifier.heightIn(
                            max = (LocalConfiguration.current.screenHeightDp * .5f).dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(state.value.orEmpty(), key = { it.id }) { reaction ->
                            FilterChip(
                                reaction.selected,
                                { react(reaction.id) },
                                enabled = !state.loading && !controller.uncertain,
                                label = {
                                    Column(
                                        Modifier.padding(vertical = 8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        KivoImage(
                                            reaction.icon,
                                            "表情 ${reaction.id}",
                                            Modifier.size(34.dp),
                                        )
                                        Text("${reaction.count}")
                                    }
                                },
                            )
                        }
                    }
                    SectionStatus(state) { controller.load() }
                    TextButton({ expanded = false }, Modifier.align(Alignment.End)) { Text("完成") }
                }
            }
        }
}

/** 公共头部在列表回收后复用同一表态状态，避免重新出现加载/错误提示改变头部高度。 */
@Composable
internal fun rememberReactionController(uuid: String, vm: CharacterViewModel): ReactionController {
    val account by vm.account.state.collectAsStateWithLifecycle()
    // LazyColumn 回收后不重新显示加载条，防止页尾高度收缩/恢复形成滚动反馈循环。
    val controller = remember(uuid, account.user?.id) { vm.reactions(uuid, account.user?.id) }
    return controller
}

@Composable
private fun SupplementPanel(
    uuid: String,
    vm: CharacterViewModel,
    link: (String) -> Unit,
    image: (String) -> Unit,
    login: () -> Unit,
) {
    val repository = vm.repository
    val account by vm.account.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var editor by rememberSaveable(uuid) { mutableStateOf(false) }
    var draft by rememberSaveable(uuid, account.user?.id) { mutableStateOf("") }
    var preview by rememberSaveable(uuid) { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var uncertain by remember { mutableStateOf(false) }
    var sendMessage by remember { mutableStateOf<String?>(null) }
    var show by rememberSaveable(uuid) { mutableStateOf(false) }
    var page by rememberSaveable(uuid) { mutableIntStateOf(1) }
    var retry by remember { mutableIntStateOf(0) }
    val state by
        remember(uuid, page, retry) { vm.supplements(uuid, page, retry > 0) }
            .collectAsStateWithLifecycle()
    KivoCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionStatus(state) { retry++ }
            Text("来自老师们的资料补充与讨论。", style = MaterialTheme.typography.bodyMedium)
            Button({ show = true }, enabled = state.value != null) {
                Text(if (state.value?.entries?.isEmpty() == true) "暂无补充信息" else "阅读全部补充信息")
            }
            OutlinedButton({ if (account.user == null) login() else editor = true }) {
                Text("我要补充")
            }
            sendMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
    if (editor)
        Dialog(
            { if (!sending) editor = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(
                Modifier.padding(16.dp)
                    .widthIn(max = 680.dp)
                    .fillMaxWidth()
                    .heightIn(max = (LocalConfiguration.current.screenHeightDp * .85f).dp),
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                LazyColumn(
                    Modifier.imePadding(),
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Text("补充角色资料", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "请附上来源、出处或依据；存疑内容请注明。支持 Markdown 与图片链接。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    item {
                        if (preview) WikiBody(draft, link, image)
                        else
                            OutlinedTextField(
                                draft,
                                {
                                    if (it.length <= 16_000) {
                                        draft = it
                                        preview = false
                                    }
                                },
                                Modifier.fillMaxWidth().heightIn(min = 180.dp),
                                label = { Text("补充内容") },
                                supportingText = { Text("${draft.length} / 16000") },
                                enabled = !sending,
                            )
                    }
                    item {
                        sendMessage?.let {
                            Text(
                                it,
                                color =
                                    if (uncertain) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    item {
                        if (uncertain)
                            TextButton({
                                editor = false
                                show = true
                                retry++
                            }) {
                                Text("先刷新核对提交结果")
                            }
                    }
                    item {
                        if (uncertain)
                            TextButton({
                                uncertain = false
                                sendMessage = null
                            }) {
                                Text("已核对，返回编辑")
                            }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton({ editor = false }, enabled = !sending) { Text("关闭") }
                            if (!preview)
                                OutlinedButton(
                                    { preview = true },
                                    enabled = draft.isNotBlank() && !sending,
                                ) {
                                    Text("预览")
                                }
                            else
                                TextButton({ preview = false }, enabled = !sending) { Text("继续编辑") }
                            Button(
                                {
                                    scope.launch {
                                        sending = true
                                        try {
                                            vm.account.supplement(uuid, draft)
                                            draft = ""
                                            preview = false
                                            editor = false
                                            page = 1
                                            retry++
                                            sendMessage = "补充信息已提交"
                                        } catch (e: CancellationException) {
                                            throw e
                                        } catch (e: Exception) {
                                            uncertain = true
                                            sendMessage =
                                                "提交未能确认，请先刷新核对，避免重复发送。${e.message.orEmpty().take(100)}"
                                        } finally {
                                            sending = false
                                        }
                                    }
                                },
                                enabled = preview && draft.isNotBlank() && !sending && !uncertain,
                            ) {
                                Text(if (sending) "提交中…" else "提交补充")
                            }
                        }
                    }
                }
            }
        }
    if (show)
        Dialog({ show = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(
                Modifier.padding(16.dp).widthIn(max = 720.dp).fillMaxWidth().fillMaxHeight(.9f),
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "补充信息",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton({ show = false }) { Icon(Icons.Outlined.Close, "关闭补充信息") }
                    }
                    SectionStatus(state) { retry++ }
                    LazyColumn(
                        Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        items(state.value?.entries.orEmpty(), key = { it.id }) { row ->
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    KivoImage(
                                        row.avatar,
                                        null,
                                        Modifier.size(36.dp).clip(RoundedCornerShape(18.dp)),
                                    )
                                    Text(row.author, style = MaterialTheme.typography.titleSmall)
                                }
                                WikiBody(row.content, link, image)
                                HorizontalDivider()
                            }
                        }
                        if (state.value?.entries?.isEmpty() == true) item { Text("还没有老师补充这份资料。") }
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton({ page-- }, enabled = page > 1 && !state.loading) { Text("上一页") }
                        Text("$page / ${state.value?.maxPage ?: 1}")
                        TextButton(
                            { page++ },
                            enabled = page < (state.value?.maxPage ?: 1) && !state.loading,
                        ) {
                            Text("下一页")
                        }
                    }
                }
            }
        }
}
