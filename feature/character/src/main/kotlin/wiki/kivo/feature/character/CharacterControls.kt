package wiki.kivo.feature.character

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import wiki.kivo.core.designsystem.*
import wiki.kivo.core.model.*

/** 面板维护草稿，关闭、返回、点击遮罩均不修改已应用条件。 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun CharacterFilterSheet(
    query: CharacterQuery,
    schools: LoadState<List<NamedReference>>,
    retry: () -> Unit,
    dismiss: () -> Unit,
    apply: (CharacterQuery) -> Unit,
) {
    var draft by remember { mutableStateOf(query.filters) }
    var group by rememberSaveable { mutableStateOf("身份与实装") }
    var picking by remember { mutableStateOf<CharacterFilter?>(null) }
    val translation = LocalKivoSettings.current.translation
    val birthday = draft["birthday"].orEmpty()
    val validBirthday =
        birthday.isBlank() ||
            (Regex("\\d{2}-\\d{2}").matches(birthday) &&
                runCatching { java.time.MonthDay.parse("--$birthday") }.isSuccess)
    fun set(key: String, value: String) {
        draft = if (value.isBlank()) draft - key else draft + (key to value)
    }
    ModalBottomSheet(
        onDismissRequest = dismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier.fillMaxWidth()
                .heightIn(max = (LocalConfiguration.current.screenHeightDp * .82f).dp)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "筛选角色",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton({ draft = emptyMap() }) { Text("重置") }
                IconButton(dismiss) { Icon(Icons.Outlined.Close, "关闭筛选") }
            }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(CharacterFilters.all.map { it.group }.distinct()) { name ->
                    FilterChip(group == name, { group = name }, label = { Text(name) })
                }
            }
            LazyColumn(
                Modifier.weight(1f, false).testTag("filter_fields"),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                items(CharacterFilters.all.filter { it.group == group }, key = { it.key }) { field
                    ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            field.label,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.semantics { heading() },
                        )
                        when (field.key) {
                            "birthday" ->
                                OutlinedTextField(
                                    birthday,
                                    { if (it.length <= 5) set(field.key, it) },
                                    label = { Text("月-日，例如 05-16") },
                                    singleLine = true,
                                    isError = !validBirthday,
                                    supportingText = {
                                        if (!validBirthday) Text("请输入有效的月-日，支持 02-29")
                                    },
                                    modifier = Modifier.fillMaxWidth().testTag("filter_birthday"),
                                )
                            "school",
                            "designer",
                            "illustrator" -> {
                                val current = draft[field.key]
                                val label =
                                    if (field.key == "school")
                                        schools.value
                                            ?.find { it.id.toString() == current }
                                            ?.name
                                            ?.display(translation) ?: current
                                    else current
                                OutlinedButton(
                                    { picking = field },
                                    Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                ) {
                                    Text(label ?: "不限 · 点击选择", Modifier.weight(1f))
                                    Icon(Icons.Outlined.ExpandMore, null)
                                }
                                if (field.key == "school") SectionStatus(schools, retry)
                            }
                            else ->
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(0.dp),
                                ) {
                                    (listOf("" to "不限") + field.options).forEach { (value, label) ->
                                        FilterChip(
                                            draft[field.key].orEmpty() == value,
                                            { set(field.key, value) },
                                            label = { Text(label) },
                                            modifier = Modifier.heightIn(min = 48.dp),
                                        )
                                    }
                                }
                        }
                    }
                }
            }
            HorizontalDivider()
            Row(
                Modifier.fillMaxWidth().padding(16.dp).imePadding(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(dismiss, Modifier.weight(1f)) { Text("取消") }
                Button(
                    { apply(query.copy(filters = draft)) },
                    enabled = validBirthday,
                    modifier = Modifier.weight(2f).testTag("apply_filters"),
                ) {
                    Text("应用 ${draft.size} 项筛选")
                }
            }
        }
    }
    picking?.let { field ->
        val options =
            if (field.key == "school")
                schools.value.orEmpty().map { it.id.toString() to it.name.display(translation) }
            else field.options
        ChoiceDialog(
            field.label,
            listOf("" to "不限") + options,
            draft[field.key].orEmpty(),
            { picking = null },
            allowCustom = field.key != "school",
        ) {
            set(field.key, it)
            picking = null
        }
    }
}

/** 大量选项使用惰性列表与本地检索；新增设计师可输入原文，避免依赖一次静态快照。 */
@Composable
internal fun ChoiceDialog(
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    dismiss: () -> Unit,
    allowCustom: Boolean = false,
    choose: (String) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    Dialog(dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            Modifier.padding(20.dp)
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .heightIn(max = (LocalConfiguration.current.screenHeightDp * .75f).dp),
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                if (options.size > 8 || allowCustom)
                    OutlinedTextField(
                        query,
                        { query = it.take(120) },
                        Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("搜索选项") },
                        leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    )
                LazyColumn(Modifier.weight(1f, false)) {
                    items(options.filter { it.second.contains(query, true) }, key = { it.first }) {
                        (value, label) ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable {
                                choose(value)
                            },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(value == selected, { choose(value) })
                            Text(label, Modifier.weight(1f))
                        }
                    }
                    if (allowCustom && query.isNotBlank() && options.none { it.first == query })
                        item {
                            TextButton({ choose(query.trim()) }) { Text("使用“${query.trim()}”精确筛选") }
                        }
                }
                TextButton(dismiss, Modifier.align(Alignment.End)) { Text("取消") }
            }
        }
    }
}

@Composable
internal fun LevelControl(label: String, count: Int, index: Int, change: (Int) -> Unit) {
    if (count <= 0) return
    var picker by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
        IconButton({ change((index - 1).coerceAtLeast(0)) }, enabled = index > 0) {
            Icon(Icons.Outlined.Remove, "降低$label")
        }
        TextButton({ picker = true }) { Text("${index+1} / $count") }
        IconButton({ change((index + 1).coerceAtMost(count - 1)) }, enabled = index < count - 1) {
            Icon(Icons.Outlined.Add, "提高$label")
        }
    }
    if (count > 1)
        Slider(
            index.toFloat(),
            { change(it.toInt()) },
            valueRange = 0f..(count - 1).toFloat(),
            steps = (count - 2).coerceAtLeast(0),
        )
    if (picker)
        ChoiceDialog(
            label,
            (0 until count).map { it.toString() to "$label ${it+1}" },
            index.toString(),
            { picker = false },
        ) {
            change(it.toInt())
            picker = false
        }
}

@Composable
internal fun FieldRows(fields: List<InfoField>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        fields.forEach { field ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    field.label,
                    Modifier.weight(.4f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                androidx.compose.foundation.text.selection.SelectionContainer(
                    Modifier.weight(.6f)
                ) {
                    Text(field.value, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
