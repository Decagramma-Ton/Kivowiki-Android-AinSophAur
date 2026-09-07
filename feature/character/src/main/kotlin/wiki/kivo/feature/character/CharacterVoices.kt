package wiki.kivo.feature.character

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.*
import kotlinx.coroutines.*
import wiki.kivo.core.designsystem.*
import wiki.kivo.core.media.CharacterExport
import wiki.kivo.core.model.*

@Composable
internal fun voiceParts(
    profile: CharacterProfile,
    vm: CharacterViewModel,
    active: Boolean = true,
): List<DetailPart> {
    var language by
        rememberSaveable(profile.student.id) {
            mutableStateOf(
                profile.voices.entries.firstOrNull { it.value.isNotEmpty() }?.key ?: "日语"
            )
        }
    var category by rememberSaveable(profile.student.id) { mutableStateOf("") }
    var search by rememberSaveable(profile.student.id) { mutableStateOf("") }
    var pending by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val token = remember { Any() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val status by vm.playback.status.collectAsStateWithLifecycle()
    DisposableEffect(Unit) { onDispose { vm.playback.release(token) } }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.playback.pause(token) }
    LaunchedEffect(active) { if (!active) vm.playback.pause(token) }
    val download =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/ogg")) { uri
            ->
            val url = pending
            if (uri != null && url != null)
                scope.launch {
                    saving = true
                    try {
                        CharacterExport.copy(context.contentResolver, uri, vm.assets.fetch(url))
                        message = "语音已保存"
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        message = "保存失败：${e.message}"
                    } finally {
                        saving = false
                    }
                }
        }
    val all = profile.voices[language].orEmpty()
    val categories = all.map { it.category }.distinct()
    val labels =
        mapOf(
            "lobby" to "大厅",
            "battle" to "战斗",
            "cafe" to "咖啡厅",
            "growup" to "成长",
            "formation" to "编队",
            "event" to "活动",
            "other" to "其他",
        )
    val rows =
        all.withIndex().filter { (_, voice) ->
            (category.isBlank() || category == voice.category) &&
                (search.isBlank() ||
                    listOf(voice.description, voice.text, voice.original).any {
                        it.contains(search, true)
                    })
        }
    return buildList {
        add(
            DetailPart("voice-controls", "角色语音") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(profile.voices.keys.toList()) { name ->
                            FilterChip(
                                language == name,
                                {
                                    vm.playback.release(token)
                                    language = name
                                    category = ""
                                },
                                label = { Text("$name ${profile.voices[name].orEmpty().size}") },
                            )
                        }
                    }
                    OutlinedTextField(
                        search,
                        { search = it.take(120) },
                        Modifier.fillMaxWidth(),
                        label = { Text("搜索台词或语音名称") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Outlined.Search, null) },
                        trailingIcon = {
                            if (search.isNotEmpty())
                                IconButton({ search = "" }) { Icon(Icons.Outlined.Close, "清空搜索") }
                        },
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(
                                category.isEmpty(),
                                { category = "" },
                                label = { Text("全部") },
                            )
                        }
                        items(categories) { key ->
                            FilterChip(
                                category == key,
                                { category = key },
                                label = { Text(labels[key] ?: key.ifBlank { "未分类" }) },
                            )
                        }
                    }
                    Text(
                        "显示 ${rows.size} / ${all.size} 条 · 点按播放，长台词可选择复制",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    status.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    message?.let { Text(it) }
                    if (saving) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (rows.isEmpty())
                        Text(if (all.isEmpty()) "暂未收录${language}语音" else "没有符合条件的语音")
                }
            }
        )
        rows.forEach { (index, voice) ->
            add(
                DetailPart("voice-$language-$index") {
                    KivoCard {
                        Column(
                            Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        voice.description.ifBlank { "语音 ${index+1}" },
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    Text(
                                        labels[voice.category] ?: voice.category,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                IconButton(
                                    { voice.file?.let { vm.playback.play(token, it) } },
                                    enabled = voice.file != null,
                                ) {
                                    if (status.url == voice.file && status.loading)
                                        CircularProgressIndicator(
                                            Modifier.size(20.dp),
                                            strokeWidth = 2.dp,
                                        )
                                    else
                                        Icon(
                                            if (status.url == voice.file && status.playing)
                                                Icons.Outlined.PauseCircle
                                            else Icons.Outlined.PlayCircle,
                                            if (status.url == voice.file && status.playing) "暂停"
                                            else "播放语音",
                                        )
                                }
                                IconButton(
                                    {
                                        pending = voice.file
                                        download.launch(
                                            "${profile.student.name}-${language}-${index+1}.ogg"
                                        )
                                    },
                                    enabled = voice.file != null && !saving,
                                ) {
                                    Icon(Icons.Outlined.Download, "保存语音")
                                }
                            }
                            SelectionContainer {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (voice.text.isNotBlank()) Text(voice.text)
                                    if (voice.original.isNotBlank() && voice.original != voice.text)
                                        Text(
                                            voice.original,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                }
                            }
                            if (voice.file == null)
                                Text("此条目暂未提供音频文件", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            )
        }
    }
}
