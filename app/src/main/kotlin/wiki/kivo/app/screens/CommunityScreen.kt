package wiki.kivo.app.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import wiki.kivo.core.content.*
import wiki.kivo.core.designsystem.*

@Composable
fun CommunityScreen(kind: String, website: (String) -> Unit, qq: () -> Unit) {
    val blocks by
        produceState<List<ContentBlock>>(emptyList(), kind) {
            if (kind == "contact")
                value = withContext(Dispatchers.Default) { ContentParser.parse(contactMarkdown) }
        }
    LazyColumn(
        Modifier.fillMaxSize().testTag("community_$kind"),
        contentPadding = PaddingValues(22.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item("intro") {
            Column(
                Modifier.widthIn(max = 760.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    if (kind == "contributor") "让奇迹发生的人们" else "为古书馆添砖加瓦",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    if (kind == "contributor") "每一页资料背后，都有老师们留下的心意。" else "一点反馈、一份资料，或一次新的相遇。",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "公开页面核对于 2026.09.07",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { website("https://kivo.wiki/$kind") }) { Text("查看网站最新内容") }
            }
        }
        if (kind == "contributor") {
            contributorGroups.forEach { group ->
                item("group_${group.title}") {
                    Column(
                        Modifier.widthIn(max = 760.dp).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SectionTitle(group.title)
                        Text(
                            group.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(
                    group.people,
                    key = { "${group.title}/${it.name}" },
                    contentType = { "contributor" },
                ) { person ->
                    KivoCard(Modifier.widthIn(max = 760.dp).fillMaxWidth()) {
                        Row(
                            Modifier.padding(18.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            KivoImage(person.avatar, null, Modifier.size(48.dp).clip(CircleShape))
                            Column(
                                Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(person.name, style = MaterialTheme.typography.titleMedium)
                                if (person.description.isNotBlank())
                                    Text(
                                        person.description,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                            }
                        }
                    }
                }
            }
            item("join") { FilledTonalButton(onClick = qq) { Text("与老师们相遇 · QQ 频道") } }
        } else
            itemsIndexed(blocks, key = { index, _ -> "block_$index" }) { _, block ->
                Box(Modifier.widthIn(max = 760.dp).fillMaxWidth()) {
                    ContentBlockView(
                        block,
                        { if (it == "https://pd.qq.com/g/Nekuso0721") qq() else website(it) },
                        website,
                    )
                }
            }
    }
}
