package wiki.kivo.feature.organization

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import wiki.kivo.core.data.OrganizationRepository
import wiki.kivo.core.designsystem.*
import wiki.kivo.core.model.*

/** 关联区以关系为分组依据，不把次要关系误当作所有成员之间都存在直接联系。 */
@Composable
fun RelationGroup(
    id: Int,
    repository: OrganizationRepository,
    open: (ContentCard) -> Unit,
    role: String = "",
    exclude: Int? = null,
) {
    var retry by rememberSaveable(id) { mutableIntStateOf(0) }
    val state by remember(id, retry) { repository.relationState(id, retry > 0) }.collectAsState()
    val translation = LocalKivoSettings.current.translation
    KivoCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionStatus(state) { retry++ }
            val relation = state.value
            if (relation != null) {
                Row(
                    Modifier.fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clickable { open(relation.card(translation)) }
                        .testTag("relation_$id"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    KivoImage(
                        relation.image,
                        null,
                        Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)),
                        ContentScale.Fit,
                    )
                    Column(Modifier.weight(1f)) {
                        if (role.isNotBlank())
                            Text(
                                role,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        Text(
                            relation.name.display(translation),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text("查看关系资料", style = MaterialTheme.typography.labelSmall)
                    }
                    Icon(Icons.Outlined.ChevronRight, null)
                }
                MemberStrip(relation.mainStudents.filterNot { it.id == exclude }, open)
            }
        }
    }
}

@Composable
fun MemberStrip(students: List<Student>, open: (ContentCard) -> Unit) {
    val translation = LocalKivoSettings.current.translation
    var costumes by rememberSaveable { mutableStateOf(false) }
    val base = students.filter { it.skin.isBlank() }
    // 与网站默认视图一致，优先原皮。仅有换装的成员仍可见，避免把整组误报为空。
    val shown = if (costumes || base.isEmpty()) students else base
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (students.any { it.skin.isNotBlank() })
            FilterChip(
                costumes,
                { costumes = !costumes },
                label = { Text("包含换装 · ${students.size}") },
            )
        if (shown.isEmpty()) Text("暂无已收录成员", style = MaterialTheme.typography.bodySmall)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(shown, key = { it.id }) { raw ->
                val student = raw.translated(translation)
                Column(
                    Modifier.width(84.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { open(student.card()) }
                        .padding(4.dp)
                        .testTag("member_${student.id}"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    KivoImage(
                        student.avatar,
                        null,
                        Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(student.displayName, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
